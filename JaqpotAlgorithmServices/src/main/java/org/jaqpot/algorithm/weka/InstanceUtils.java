/*
 *
 * JAQPOT Quattro
 *
 * JAQPOT Quattro and the components shipped with it, in particular:
 * (i)   JaqpotCoreServices
 * (ii)  JaqpotAlgorithmServices
 * (iii) JaqpotDB
 * (iv)  JaqpotDomain
 * (v)   JaqpotEAR
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
package org.jaqpot.algorithm.weka;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jaqpot.core.model.dto.dataset.DataEntry;
import org.jaqpot.core.model.dto.dataset.Dataset;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

/**
 *
 * @author hampos
 */
public class InstanceUtils {

    public static Instances createFromDataset(Dataset dataset, String predictionFeature) {

        List<Attribute> attributes = dataset.getDataEntry()
                .stream()
                .findFirst()
                .get()
                .getValues()
                .keySet()
                .stream()
                .map(feature -> {
                    return new Attribute(feature);
                }).collect(Collectors.toList());

        Instances data = new Instances(dataset.getDatasetURI(), new ArrayList<>(attributes), dataset.getDataEntry().size());
        data.setClass(data.attribute(predictionFeature));

        for (DataEntry dataEntry : dataset.getDataEntry()) {
            Instance instance = new DenseInstance(dataEntry.getValues().size());
            dataEntry.getValues().entrySet().stream().forEach(entry -> {
                instance.setValue(data.attribute(entry.getKey()), Double.parseDouble(entry.getValue().toString()));
            });
            data.add(instance);
        }
        return data;
    }

    public static Instances createFromDataset(Dataset dataset) {

        List<Attribute> attributes = dataset.getDataEntry()
                .stream()
                .findFirst()
                .get()
                .getValues()
                .keySet()
                .stream()
                .map(feature -> {
                    return new Attribute(feature);
                }).collect(Collectors.toList());

        Instances data = new Instances(dataset.getDatasetURI(), new ArrayList<>(attributes), dataset.getDataEntry().size());

        for (DataEntry dataEntry : dataset.getDataEntry()) {
            Instance instance = new DenseInstance(dataEntry.getValues().size());
            dataEntry.getValues().entrySet().stream().forEach(entry -> {
                instance.setValue(data.attribute(entry.getKey()), Double.parseDouble(entry.getValue().toString()));
            });
            data.add(instance);
        }
        return data;
    }

}
