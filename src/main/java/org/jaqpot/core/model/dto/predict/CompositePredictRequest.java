/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jaqpot.core.model.dto.predict;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author anpan
 */
@XmlRootElement
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompositePredictRequest {
    
    private String  modelId;
    private Map<String, Object>   parameters;
    private String  predictionFeature;
    private String  smilesInput;
    
    
    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }
    
    public Map<String, Object>  getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object>  parameters) {
        this.parameters = parameters;
    }
    
    public String getPredictionFeature() {
        return predictionFeature;
    }

    public void setPredictionFeature(String predictionFeature) {
        this.predictionFeature= predictionFeature;
    }
    
    public String getSmilesInput() {
        return smilesInput;
    }

    public void setSmilesInput(String smilesInput) {
        this.smilesInput = smilesInput;
    }
    
    
    
}
