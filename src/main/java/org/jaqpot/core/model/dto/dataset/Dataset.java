/*
 *
 * JAQPOT Quattro
 *
 * JAQPOT Quattro and the components shipped with it (web applications and beans)
 * are licensed by GPL v3 as specified hereafter. Additional components may ship
 * with some other licence as will be specified therein.
 *
 * Copyright (C) 2014-2015 KinkyDesign (Charalampos Chomenidis, Pantelis Sopasakis)
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
 * 
 * Source code:
 * The source code of JAQPOT Quattro is available on github at:
 * https://github.com/KinkyDesign/JaqpotQuattro
 * All source files of JAQPOT Quattro that are stored on github are licensed
 * with the aforementioned licence. 
 */
package org.jaqpot.core.model.dto.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.dataformat.csv.*;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import org.jaqpot.core.data.serialize.*;
import java.util.logging.Logger;
import org.jaqpot.core.data.serialize.JacksonJSONSerializer;

import org.jaqpot.core.model.DataEntry;
import org.jaqpot.core.model.JaqpotEntity;

/**
 *
 * @author Pantelis Sopasakis
 * @author Charalampos Chomenidis
 *
 */
@XmlRootElement
@JsonIgnoreProperties(ignoreUnknown = true)
public class Dataset extends JaqpotEntity {

    public enum DescriptorCategory {

        EXPERIMENTAL("Experimental data", "Nanomaterial properties derived from experiments"),
        IMAGE("ImageAnalysis descriptors", "Descriptors derived from analyzing substance images by the ImageAnalysis software."),
        GO("GO descriptors", "Descriptors derived by proteomics data."),
        MOPAC("Mopac descriptors", "Descriptors derived by crystallographic data."),
        CDK("CDK descriptors", "Descriptors derived from cdk software."),
        PREDICTED("Predicted descriptors", "Descriptors derived from algorithm predictions."),
        FORPREDICTION("Created for prediction", "Dataset created and is temp for prediction");

        private final String name;
        private final String description;

        private DescriptorCategory(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return this.name;
        }

        public String getDescription() {
            return this.description;
        }

    }

    public enum DatasetExistence {

        UPLOADED("Uploaded", "Dataset uploaded from user"),
        CREATED("Created", "Dataset created from outer source"),
        TRANFORMED("Transformed", "Dataset transformed"),
        PREDICTED("Predicted", "Dataset is a result of a prediction"),
        DESCRIPTORSADDED("Descriptors added", "Dataset has added descriptors"),
        FROMPRETRAINED("Pretrained empty", "Dataset empty for pretrained model"),
        FORPREDICTION("Created for prediction", "Dataset created and is temp for prediction");

        private final String name;
        private final String description;

        private DatasetExistence(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return this.name;
        }

        public String getDescription() {
            return this.description;
        }

    }

    private String datasetURI;

    private String byModel;

    private List<DataEntry> dataEntry;

    private Set<FeatureInfo> features;

    private String datasetPic;

    private Integer totalRows;
    private Integer totalColumns;

    private Set<DescriptorCategory> descriptors;
    private DatasetExistence existence;

    private Boolean onTrash;

    public String getDatasetURI() {
        return datasetURI;
    }

    public void setDatasetURI(String datasetURI) {
        this.datasetURI = datasetURI;
    }

    public String getByModel() {
        return byModel;
    }

    public void setByModel(String byModel) {
        this.byModel = byModel;
    }

    public List<DataEntry> getDataEntry() {
        return dataEntry;
    }

    public void setDataEntry(List<DataEntry> dataEntry) {
        this.dataEntry = dataEntry;
    }

    public Set<FeatureInfo> getFeatures() {
        return features;
    }

    public void setFeatures(Set<FeatureInfo> features) {
        this.features = features;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Integer getTotalColumns() {
        return totalColumns;
    }

    public void setTotalColumns(Integer totalColumns) {
        this.totalColumns = totalColumns;
    }

    public Set<DescriptorCategory> getDescriptors() {
        return descriptors;
    }

    public void setDescriptors(Set<DescriptorCategory> descriptors) {
        this.descriptors = descriptors;
    }

    public String getDatasetPic() {
        return datasetPic;
    }

    public void setDatasetPic(String datasetPic) {
        this.datasetPic = datasetPic;
    }

    public DatasetExistence getExistence() {
        return existence;
    }

    public void setExistence(DatasetExistence existence) {
        this.existence = existence;
    }

    public Boolean getOnTrash() {
        return onTrash;
    }

    public void setOnTrash(Boolean onTrash) {
        this.onTrash = onTrash;
    }

    public FeatureInfo getFeature(String key) {

        return this.getFeatures().stream()
                .filter(fi -> fi.getKey().equals(key))
                .findAny()
                .get();

    }

    public void setFeature(FeatureInfo fi, String key) {
       //Feature fi exists in the dataset
        HashSet<FeatureInfo> fis = new HashSet();
        this.getFeatures().stream()
                .filter(f -> f.getKey().equals(key))
                .forEach(f->{
                       if(f.getKey().equals(key)){
                         fis.add(f);
                       }
                });
        //Feature fi exists in the dataset. The key of fi is set explicitely to key.
        if (!fis.isEmpty()) {
            fis.stream()
                    .forEach((FeatureInfo f) -> {
                        f.setCategory(fi.getCategory());
                        f.setConditions(fi.getConditions());
                        f.setName(fi.getName());
                        f.setOnt(fi.getOnt());
                        f.setURI(fi.getURI());
                        f.setUnits(fi.getUnits());
                    });
        } else {
            this.getFeatures().add(fi);
        }

    }

    
    
    
    @Override
    public String toString() {
        return "Dataset{" + "datasetURI=" + datasetURI + ", dataEntry=" + dataEntry + '}';
    }

}
