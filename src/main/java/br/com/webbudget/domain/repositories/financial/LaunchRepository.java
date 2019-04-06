/*
 * Copyright (C) 2019 Arthur Gregorio, AG.Software
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
package br.com.webbudget.domain.repositories.financial;

import br.com.webbudget.domain.entities.financial.FixedMovement;
import br.com.webbudget.domain.entities.financial.Launch;
import br.com.webbudget.domain.repositories.DefaultRepository;
import org.apache.deltaspike.data.api.Query;
import org.apache.deltaspike.data.api.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link Launch} entity repository
 *
 * @author Arthur Gregorio
 *
 * @version 1.0.0
 * @since 3.0.0, 27/03/2019
 */
@Repository
public interface LaunchRepository extends DefaultRepository<Launch> {

    /**
     * Find the last {@link Launch} for a give {@link FixedMovement}
     *
     * @param fixedMovementId of the {@link FixedMovement} to find his launch
     * @return an {@link Optional} of the {@link Launch}
     */
    @Query("SELECT lc.quoteNumber " +
            "FROM Launch lc " +
            "WHERE lc.id = (SELECT MAX(id) FROM Launch WHERE fixedMovement.id = ?1)")
    Optional<Integer> findLastLaunchCounterFor(long fixedMovementId);

    /**
     * Find all {@link Launch} for a given {@link FixedMovement}
     *
     * @param fixedMovement to search for the {@link Launch}
     * @return the {@link List} of {@link Launch} found
     */
    List<Launch> findByFixedMovement(FixedMovement fixedMovement);
}
