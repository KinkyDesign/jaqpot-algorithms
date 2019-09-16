/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jaqpot.core.model.dto.dataset;

import java.util.Set;

/**
 *
 * @author anpan
 */
public interface Formatter {
    
    
    Dataset format(Dataset dataset, Set<FeatureInfo> features);
    
    //Dataset format(Dataset dataset);
}
