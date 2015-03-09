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
package br.com.webbudget.infraestructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 *
 * @author Arthur Gregorio
 *
 * @version 1.0.0
 * @since 1.1.0, 07/03/2015
 */
@Configuration
@ComponentScan({
    "br.com.webbudget.domain",
    "br.com.webbudget.infraestructure"
})
@EnableAspectJAutoProxy(proxyTargetClass = true)
@PropertySource("classpath:config/webbudget.properties")
public class ContextConfiguration {

    /**
     *
     * @return
     */
    @Bean
    public ReloadableResourceBundleMessageSource configureMessageSource() {

        final ReloadableResourceBundleMessageSource source
                = new ReloadableResourceBundleMessageSource();

        source.setBasename("classpath:/i18n/messages");
        source.setUseCodeAsDefaultMessage(true);
        source.setDefaultEncoding("UTF-8");
        source.setCacheSeconds(0);

        return source;
    }

    /**
     *
     * @return
     */
    @Bean
    public PasswordEncoder configurePasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}