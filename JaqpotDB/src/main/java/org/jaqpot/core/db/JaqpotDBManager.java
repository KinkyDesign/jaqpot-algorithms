/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jaqpot.core.db;

import org.jaqpot.core.db.entitymanager.JaqpotEntityManager;
import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import org.jaqpot.core.annotations.MongoDB;

/**
 *
 * @author hampos
 */
@Singleton
public class JaqpotDBManager {

    @Inject
    @MongoDB
    private JaqpotEntityManager em;

    @Produces
    public JaqpotEntityManager getEntityManager() {
        return em;
    }

}
