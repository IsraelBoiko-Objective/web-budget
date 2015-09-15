/*
 * Copyright (C) 2015 Arthur Gregorio, AG.Software
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package br.com.webbudget.domain.service;

import br.com.webbudget.domain.entity.card.CardInvoice;
import br.com.webbudget.domain.entity.movement.Apportionment;
import br.com.webbudget.domain.entity.movement.CostCenter;
import br.com.webbudget.domain.entity.movement.FinancialPeriod;
import br.com.webbudget.domain.entity.movement.Movement;
import br.com.webbudget.domain.entity.movement.MovementClass;
import br.com.webbudget.domain.entity.movement.MovementClassType;
import br.com.webbudget.domain.entity.movement.MovementStateType;
import br.com.webbudget.domain.entity.movement.Payment;
import br.com.webbudget.domain.entity.movement.PaymentMethodType;
import br.com.webbudget.domain.entity.wallet.Wallet;
import br.com.webbudget.domain.entity.wallet.WalletBalanceType;
import br.com.webbudget.domain.misc.BalanceBuilder;
import br.com.webbudget.domain.misc.dto.MovementFilter;
import br.com.webbudget.domain.misc.events.UpdateBalance;
import br.com.webbudget.domain.misc.ex.WbDomainException;
import br.com.webbudget.domain.misc.model.Page;
import br.com.webbudget.domain.misc.model.PageRequest;
import br.com.webbudget.domain.repository.card.ICardInvoiceRepository;
import br.com.webbudget.domain.repository.movement.IApportionmentRepository;
import br.com.webbudget.domain.repository.movement.ICostCenterRepository;
import br.com.webbudget.domain.repository.movement.IMovementRepository;
import br.com.webbudget.domain.repository.movement.IMovementClassRepository;
import br.com.webbudget.domain.repository.movement.IPaymentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.transaction.Transactional;

/**
 *
 * @author Arthur Gregorio
 *
 * @version 1.2.0
 * @since 1.0.0, 04/03/2014
 */
@ApplicationScoped
public class MovementService {

    @Inject
    @UpdateBalance
    private Event<BalanceBuilder> updateBalanceEvent;

    @Inject
    private IPaymentRepository paymentRepository;
    @Inject
    private IMovementRepository movementRepository;
    @Inject
    private ICostCenterRepository costCenterRepository;
    @Inject
    private ICardInvoiceRepository cardInvoiceRepository;
    @Inject
    private IApportionmentRepository apportionmentRepository;
    @Inject
    private IMovementClassRepository movementClassRepository;
    
    /**
     *
     * @param movementClass
     */
    @Transactional
    public void saveMovementClass(MovementClass movementClass) {

        final MovementClass found = this.findMovementClassByNameAndTypeAndCostCenter(movementClass.getName(),
                movementClass.getMovementClassType(), movementClass.getCostCenter());

        if (found != null) {
            throw new WbDomainException("movement-class.validate.duplicated");
        }

        // valida o orcamento, se estiver ok, salva!
        this.hasValidBudget(movementClass);
           
        this.movementClassRepository.save(movementClass);
    }

    /**
     *
     * @param movementClass
     * @return
     */
    @Transactional
    public MovementClass updateMovementClass(MovementClass movementClass) {

        final MovementClass found = this.findMovementClassByNameAndTypeAndCostCenter(movementClass.getName(),
                movementClass.getMovementClassType(), movementClass.getCostCenter());

        if (found != null && !found.equals(movementClass)) {
            throw new WbDomainException("movement-class.validate.duplicated");
        }

        // valida o orcamento, se estiver ok, salva!
        this.hasValidBudget(movementClass);

        return this.movementClassRepository.save(movementClass);
    }

    /**
     * Aqui realizamos a regra de validacao do orcamento pelo centro de custo
     * 
     * @param movementClass a classe a ser validada
     */
    private boolean hasValidBudget(MovementClass movementClass) {
        
        final CostCenter costCenter = movementClass.getCostCenter();
        final MovementClassType classType = movementClass.getMovementClassType();

        if (costCenter.controlBudget(classType)) {
            
            final List<MovementClass> classes = 
                    this.listMovementClassesByCostCenterAndType(costCenter, classType);

            BigDecimal consumedBudget = classes.stream()
                    .filter(mc -> !mc.equals(movementClass))
                    .map(MovementClass::getBudget)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal availableBudget;

            if (classType == MovementClassType.IN) {
                availableBudget = costCenter.getRevenuesBudget().subtract(consumedBudget);
            } else {
                availableBudget = costCenter.getExpensesBudget().subtract(consumedBudget);
            }
     
            // caso o valor disponivel seja menor que o desejado, exception!
            if (availableBudget.compareTo(movementClass.getBudget()) < 0) {
                
                final String value = "R$ " + String.format("%10.2f", availableBudget);
                
                throw new WbDomainException("movement-class.validate.no-budget", value);
            }
        }
        return true;
    }

    /**
     *
     * @param movementClass
     */
    @Transactional
    public void deleteMovementClass(MovementClass movementClass) {
        this.movementClassRepository.delete(movementClass);
    }

    /**
     *
     * @param movement
     * @return
     */
    @Transactional
    public Movement saveMovement(Movement movement) {

        // validamos se os rateios estao corretos
        if (!movement.getApportionments().isEmpty()) {
            if (!movement.isApportionmentsValid()) {
                throw new WbDomainException("movement.validate.apportionment-value",
                        movement.getApportionmentsDifference());
            }
        } else {
            throw new WbDomainException("movement.validate.empty-apportionment");
        }

        // se for uma edicao, checa se existe alguma inconsistencia
        if (movement.isSaved()) {

            if (movement.getFinancialPeriod().isClosed()) {
                throw new WbDomainException("movement.validate.closed-financial-period");
            }

            // remove algum rateio editado
            for (Apportionment apportionment : movement.getDeletedApportionments()) {
                this.apportionmentRepository.delete(apportionment);
            }
        }

        // pega os rateios antes de salvar o movimento para nao perder a lista
        final List<Apportionment> apportionments = movement.getApportionments();

        // salva o movimento
        movement = this.movementRepository.save(movement);

        // salva os rateios
        for (Apportionment apportionment : apportionments) {
            apportionment.setMovement(movement);
            this.apportionmentRepository.save(apportionment);
        }

        // busca novamente as classes
        movement.getApportionments().clear();
        movement.setApportionments(new ArrayList<>(
                this.apportionmentRepository.listByMovement(movement)));

        return movement;
    }

    /**
     *
     * @param movement
     */
    @Transactional
    public void payAndUpdateMovement(Movement movement) {

        // salva o pagamento
        final Payment payment = this.paymentRepository.save(movement.getPayment());

        // seta no pagamento e atualiza o movimento
        movement.setPayment(payment);
        movement.setMovementStateType(MovementStateType.PAID);

        this.movementRepository.save(movement);

        // atualizamos os saldos das carteiras quando pagamento em dinheiro
        if (payment.getPaymentMethodType() == PaymentMethodType.IN_CASH
                || payment.getPaymentMethodType() == PaymentMethodType.DEBIT_CARD) {

            Wallet wallet;

            if (payment.getPaymentMethodType() == PaymentMethodType.DEBIT_CARD) {
                wallet = payment.getCard().getWallet();
            } else {
                wallet = payment.getWallet();
            }

            // atualizamos o novo saldo
            final BalanceBuilder builder = new BalanceBuilder();

            final BigDecimal oldBalance = wallet.getBalance();

            builder.forWallet(wallet)
                    .withOldBalance(oldBalance)
                    .withMovementedValue(movement.getValue())
                    .referencingMovement(movement.getCode());

            if (movement.getDirection() == MovementClassType.OUT) {
                builder.withActualBalance(oldBalance.subtract(movement.getValue()))
                        .andType(WalletBalanceType.PAYMENT);
            } else {
                builder.withActualBalance(oldBalance.add(movement.getValue()))
                        .andType(WalletBalanceType.REVENUE);
            }

            this.updateBalanceEvent.fire(builder);
        }
    }

    /**
     *
     * @param movement
     */
    @Transactional
    public void deleteMovement(Movement movement) {

        if (movement.getFinancialPeriod().isClosed()) {
            throw new WbDomainException("movement.validate.closed-financial-period");
        }

        // se tem vinculo com fatura, nao pode ser excluido
        if (movement.isCardInvoicePaid()) {
            throw new WbDomainException("movement.validate.has-card-invoice");
        }

        // devolve o saldo na carteira se for o caso
        if (movement.getMovementStateType() == MovementStateType.PAID
                && movement.getPayment().getPaymentMethodType() == PaymentMethodType.IN_CASH) {

            Wallet paymentWallet;

            final Payment payment = movement.getPayment();

            if (payment.getPaymentMethodType() == PaymentMethodType.DEBIT_CARD) {
                paymentWallet = payment.getCard().getWallet();
            } else {
                paymentWallet = payment.getWallet();
            }

            final BigDecimal movimentedValue;

            // se entrada, valor negativo, se saida valor positivo
            if (movement.getDirection() == MovementClassType.OUT) {
                movimentedValue = movement.getValue();
            } else {
                movimentedValue = movement.getValue().negate();
            }

            // tratamos a devoluacao do saldo
            final BalanceBuilder builder = new BalanceBuilder();

            builder.forWallet(paymentWallet)
                    .withOldBalance(paymentWallet.getBalance())
                    .withActualBalance(paymentWallet.getBalance().add(movimentedValue))
                    .withMovementedValue(movimentedValue)
                    .andType(WalletBalanceType.BALANCE_RETURN);

            this.updateBalanceEvent.fire(builder);
        }

        this.movementRepository.delete(movement);
    }

    /**
     * Metodo que realiza o processo de deletar um movimento vinculado a uma
     * fatura de cartao, deletando a fatura e limpando as flags dos movimentos
     * vinculados a ela
     *
     * @param movement o movimento referenciando a invoice
     */
    @Transactional
    public void deleteCardInvoiceMovement(Movement movement) {

        final CardInvoice cardInvoice
                = this.cardInvoiceRepository.findByMovement(movement);

        // se a invoice for de um periodo fechado, bloqueia o processo
        if (cardInvoice.getFinancialPeriod().isClosed()) {
            throw new WbDomainException("movement.validate.closed-financial-period");
        }

        // listamos os movimentos da invoice
        final List<Movement> invoiceMovements
                = this.movementRepository.listByCardInvoice(cardInvoice);

        // limpamos as flags para cada moveimento
        invoiceMovements.stream().map((invoiceMovement) -> {
            invoiceMovement.setCardInvoice(null);
            return invoiceMovement;
        }).map((invoiceMovement) -> {
            invoiceMovement.setCardInvoicePaid(false);
            return invoiceMovement;
        }).forEach((invoiceMovement) -> {
            this.movementRepository.save(invoiceMovement);
        });

        // se houve pagamento, devolve o valor para a origem
        if (movement.getMovementStateType() == MovementStateType.PAID) {

            final Wallet paymentWallet = movement.getPayment().getWallet();

            final BigDecimal oldBalance = paymentWallet.getBalance();
            final BigDecimal newBalance = oldBalance.add(movement.getValue());

            final BalanceBuilder builder = new BalanceBuilder();

            builder.forWallet(paymentWallet)
                    .withOldBalance(oldBalance)
                    .withActualBalance(newBalance)
                    .withMovementedValue(movement.getValue())
                    .andType(WalletBalanceType.BALANCE_RETURN);

            this.updateBalanceEvent.fire(builder);
        }

        // deletamos a movimentacao da invoice
        this.movementRepository.delete(movement);

        // deletamos a invoice
        this.cardInvoiceRepository.delete(cardInvoice);
    }

    /**
     *
     * @param costCenter
     */
    @Transactional
    public void saveCostCenter(CostCenter costCenter) {

        final CostCenter found = this.findCostCenterByNameAndParent(costCenter.getName(),
                costCenter.getParentCostCenter());

        if (found != null) {
            throw new WbDomainException("cost-center.validate.duplicated");
        }

        this.costCenterRepository.save(costCenter);
    }

    /**
     *
     * @param costCenter
     * @return
     */
    @Transactional
    public CostCenter updateCostCenter(CostCenter costCenter) {

        final CostCenter found = this.findCostCenterByNameAndParent(costCenter.getName(),
                costCenter.getParentCostCenter());

        if (found != null && !found.equals(costCenter)) {
            throw new WbDomainException("cost-center.validate.duplicated");
        }

        return this.costCenterRepository.save(costCenter);
    }

    /**
     *
     * @param costCenter
     */
    @Transactional
    public void deleteCostCenter(CostCenter costCenter) {
        this.costCenterRepository.delete(costCenter);
    }

    /**
     *
     * @param movementClassId
     * @return
     */
    public MovementClass findMovementClassById(long movementClassId) {
        return this.movementClassRepository.findById(movementClassId, false);
    }

    /**
     *
     * @param costCenterId
     * @return
     */
    public CostCenter findCostCenterById(long costCenterId) {
        return this.costCenterRepository.findById(costCenterId, false);
    }

    /**
     *
     * @param movementId
     * @return
     */
    public Movement findMovementById(long movementId) {
        return this.movementRepository.findById(movementId, false);
    }

    /**
     *
     * @param isBlocked
     * @return
     */
    public List<MovementClass> listMovementClasses(Boolean isBlocked) {
        return this.movementClassRepository.listByStatus(isBlocked);
    }
    
    /**
     * 
     * @param isBlocked
     * @param pageRequest
     * @return 
     */
    public Page<MovementClass> listMovementClassesLazily(Boolean isBlocked, PageRequest pageRequest) {
        return this.movementClassRepository.listLazilyByStatus(isBlocked, pageRequest);
    }

    /**
     *
     * @param costCenter
     * @param type
     * @return
     */
    public List<MovementClass> listMovementClassesByCostCenterAndType(CostCenter costCenter, MovementClassType type) {
        return this.movementClassRepository.listByCostCenterAndType(costCenter, type);
    }

    /**
     *
     * @param isBlocked
     * @return
     */
    public List<CostCenter> listCostCenters(Boolean isBlocked) {
        return this.costCenterRepository.listByStatus(isBlocked);
    }
    
    /**
     * 
     * @param isBlocked
     * @param pageRequest
     * @return 
     */
    public Page<CostCenter> listCostCentersLazily(Boolean isBlocked, PageRequest pageRequest) {
        return this.costCenterRepository.listLazilyByStatus(isBlocked, pageRequest);
    }

    /**
     *
     * @param financialPeriod
     * @return
     */
    public List<Movement> listMovementsByPeriod(FinancialPeriod financialPeriod) {
        return this.movementRepository.listByPeriod(financialPeriod);
    }

    /**
     *
     * @return
     */
    public List<Movement> listMovementsByActiveFinancialPeriod() {
        return this.movementRepository.listByActiveFinancialPeriod();
    }

    /**
     *
     * @param dueDate
     * @param showOverdue
     * @return
     */
    public List<Movement> listMovementsByDueDate(Date dueDate, boolean showOverdue) {
        return this.movementRepository.listByDueDate(dueDate, showOverdue);
    }

    /**
     * 
     * @param filter
     * @param pageRequest
     * @return 
     */
    public Page<Movement> listMovementsByFilter(MovementFilter filter, PageRequest pageRequest) {
        return this.movementRepository.listLazilyByFilter(filter, pageRequest);
    }

    /**
     *
     * @param name
     * @param type
     * @param costCenter
     * @return
     */
    public MovementClass findMovementClassByNameAndTypeAndCostCenter(String name, MovementClassType type, CostCenter costCenter) {
        return this.movementClassRepository.findByNameAndTypeAndCostCenter(name, type, costCenter);
    }

    /**
     *
     * @param name
     * @param parent
     * @return
     */
    public CostCenter findCostCenterByNameAndParent(String name, CostCenter parent) {
        return this.costCenterRepository.findByNameAndParent(name, parent);
    }

    /**
     *
     * @param cardInvoice
     * @return
     */
    public List<Movement> listMovementsByCardInvoice(CardInvoice cardInvoice) {
        return this.movementRepository.listByCardInvoice(cardInvoice);
    }
}
