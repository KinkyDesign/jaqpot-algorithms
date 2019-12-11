/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jaqpot.core.model.dto.dataset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jaqpot.core.model.DataEntry;
import java.lang.Math;
import static java.util.Collections.max;
import java.util.Comparator;

/**
 *
 * @author anpan
 */
//Resolve mismatch with calculations' format of generated datasets by descriptors. 
//Replace feature keys that currently contain feature names with keys.
//Update dataentries with the the changes made to features wherever needed.
public class CalculationsFormatter implements Formatter {

    @Override
    public Dataset format(Dataset dataset, Set<FeatureInfo> features) {

        //Set<FeatureInfo> features = dataset.getFeatures();
        Dataset result = new Dataset();
        result.setId(dataset.getId());
        result.setMeta(dataset.getMeta());

        int maxKey;
        int size;
        ArrayList<FeatureInfo> featuresList = new ArrayList();

        HashSet<String> keySet = new HashSet();
        featuresList = features.stream()
                .filter(f -> f.getKey() != null)
                .collect(Collectors.toCollection(ArrayList::new));

        featuresList.forEach(f -> keySet.add(f.getKey()));
        maxKey = keySet.stream().mapToInt(key -> Integer.valueOf(key)).max().getAsInt();
        size = features.size() - featuresList.size();

        ArrayList<FeatureInfo> featuresNullKeys = features.stream()
                .filter(f -> f.getKey() == null)
                .collect(Collectors.toCollection(ArrayList::new));

        ArrayList<FeatureInfo> featuresWithKeys = new ArrayList();
        featuresWithKeys = IntStream.range(maxKey + 1, maxKey + size + 1)
                .mapToObj((int in) -> {
                    FeatureInfo fi = new FeatureInfo();
                    fi.setName(featuresNullKeys.get(in - maxKey - 1).getName());
                    fi.setURI(featuresNullKeys.get(in - maxKey - 1).getURI());
                    fi.setKey(Integer.toString(in));
                    return fi;
                })
                .collect(Collectors.toCollection(ArrayList::new));

        ArrayList<FeatureInfo> fs = new ArrayList();
        featuresWithKeys.forEach(f -> fs.add(f));

        featuresList.forEach(f -> fs.add(f));

        result.setFeatures(new HashSet(fs));

        HashMap<String, String> nameKeys = new HashMap();
        featuresWithKeys.stream().forEach(f -> {
            nameKeys.put(f.getName(), f.getKey());
        });

        List<DataEntry> dataEntries = dataset.getDataEntry()
                .parallelStream()
                .map(dataEntry -> {
                    DataEntry newEntry = new DataEntry();
                    newEntry.setEntryId(dataEntry.getEntryId());
                    TreeMap<String, Object> values = new TreeMap<>();
                    dataEntry.getValues()
                    .keySet()
                    .stream()
                    //.filter(key -> keyNames.containsKey(key))
                    .forEach(key -> {
                        if (nameKeys.containsKey(key)) {
                            values.put(nameKeys.get(key), dataEntry.getValues().get(key));
                        } else {
                            values.put(key, dataEntry.getValues().get(key));
                        }
                    });
                    newEntry.setValues(values);
                    return newEntry;
                })
                .collect(Collectors.toList());

        result.setDataEntry(dataEntries);

        return result;
    }

}
