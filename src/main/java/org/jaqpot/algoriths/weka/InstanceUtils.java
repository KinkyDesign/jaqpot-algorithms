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
package org.jaqpot.algoriths.weka;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.jaqpot.algorithms.dto.dataset.Dataset;
import org.jaqpot.algorithms.dto.dataset.FeatureInfo;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

/**
 *
 * @author Charalampos Chomenidis
 * @author Pantelis Sopasakis
 */
public class InstanceUtils {

    public static Instances createFromDataset(Dataset dataset, String predictionFeatureKey) {

        FastVector attrInfo = new FastVector();
        //  HashMap auxMap=new HashMap();
//        dataset.getFeatures().stream()
//                .map(featureInfo->{
//                   
//                     return new Attribute(featureInfo.getName());
//                })
//                .forEach(a -> {
//                   attrInfo.addElement(a);
//               });
        dataset.getDataEntry()
                .stream()
                .findFirst()
                .get()
                .getValues()
                .keySet()
                .stream()
                .filter(key -> !key.equals("0"))
                .map(featureKey -> {

                    return new Attribute(featureKey);

                })
                .forEach(a -> {
                    attrInfo.addElement(a);
                });
        
//        dataset.getDataEntry()
//                .stream()
//                .findFirst()
//                .get()
//                .getValues()
//                .keySet()
//                .stream()
//                .map(featureKey -> {
//                    String featName = dataset.getFeatures().stream()
//                    .filter(f -> f.getKey().equals(featureKey))
//                    .findAny().get().getName();
//                    return new Attribute(featName);
//                }).forEach(a -> {
//                    attrInfo.addElement(a);
//                });

        Instances data = new Instances(dataset.getDatasetURI(), attrInfo, dataset.getDataEntry().size());

//        String pfName = dataset.getFeatures().stream()
//                .filter(f -> f.getURI().equals(predictionFeature))
//                .findAny().get().getName();

        //data.setClass(data.attribute(pfName));
        data.setClass(data.attribute(predictionFeatureKey));
        HashSet test = new HashSet();
        dataset.getDataEntry().stream().forEach(dataEntry -> {
            //Instance instance = new Instance(dataEntry.getValues().size());
            test.addAll(dataEntry.getValues().entrySet());
        });
        dataset.getDataEntry().stream()
                .map((dataEntry) -> {
                    Instance instance = new Instance(attrInfo.size());
                    dataEntry.getValues().entrySet().stream()
                    .filter(entry -> !entry.getKey().equals("0"))
                    .forEach(entry -> {
//                        String featName = dataset.getFeatures().stream()
//                        .filter(f -> f.getKey().equals(entry.getKey()))
//                        .findAny().get().getName();
                        instance.setValue(data.attribute(entry.getKey()), Double.parseDouble(entry.getValue().toString()));
                        //instance.setValue(data.attribute(featName), Double.parseDouble(entry.getValue().toString()));
                    });
                    return instance;
                })
                .forEach((instance) -> {
                    data.add(instance);
                });
        return data;
    }

    public static Instances createFromDataset(Dataset dataset) {

        FastVector attrInfo = new FastVector();
        dataset.getDataEntry()
                .stream()
                .findFirst()
                .get()
                .getValues()
                .keySet()
                .stream()
                //.filter(key -> !key.equals("0"))
                .map(featureKey -> {

                    return new Attribute(featureKey);

                })
                .forEach(a -> {
                    attrInfo.addElement(a);
                });

        Instances data = new Instances(dataset.getDatasetURI(), attrInfo, dataset.getDataEntry().size());

        HashSet test = new HashSet();
        dataset.getDataEntry().stream().forEach(dataEntry -> {
            //Instance instance = new Instance(dataEntry.getValues().size());
            test.addAll(dataEntry.getValues().entrySet());
        });

        dataset.getDataEntry().stream().map((dataEntry) -> {
            Instance instance = new Instance(dataEntry.getValues().size());
            dataEntry.getValues().entrySet().stream()
                    //.filter(entry -> !entry.getKey().equals("0"))
                    .forEach(entry -> {
//                        String featName = dataset.getFeatures().stream()
//                                                    .filter(f->f.getKey().equals(entry.getKey()))
//                                                    .findAny().get().getName();
                        String featKey = entry.getKey();
                        //instance.setValue(data.attribute(featName), Double.parseDouble(entry.getValue().toString()));
                        instance.setValue(data.attribute(featKey), Double.parseDouble(entry.getValue().toString()));
                    });
            return instance;
        }).forEach((instance) -> {
            data.add(instance);
        });
        return data;
    }

}
