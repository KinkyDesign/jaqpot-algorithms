/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jaqpot.core.model.dto.dataset;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jaqpot.core.model.DataEntry;

/**
 *
 * @author anpan
 */
//Resolve mismatch with legacy format of Datasets. Replace feature uris as keys in value TreeMaps with keys.
public class LegacyFormatter implements Formatter {

    @Override
    public Dataset format(Dataset dataset, Set<FeatureInfo> dsFeatures) {

        Dataset result = new Dataset();
        result.setId(dataset.getId());
        result.setMeta(dataset.getMeta());

        HashMap<String, String> keyUriMap = new HashMap();

        dsFeatures.stream().forEach(feature -> {
            keyUriMap.put(feature.getURI(), feature.getKey());
        });

        List<DataEntry> dataEntries = dataset.getDataEntry()
                .parallelStream()
                .map(dataEntry -> {
                    DataEntry newEntry = new DataEntry();
                    TreeMap<String, Object> values = new TreeMap<>();
                    dataEntry.getValues()
                    .keySet()
                    .stream()
                    //.filter(feature -> features.contains(feature))
                    .filter(uri -> keyUriMap.containsKey(uri))
                    .forEach(uri -> {

                        values.put(keyUriMap.get(uri), dataEntry.getValues().get(uri));

                    });
                    newEntry.setValues(values);
                    return newEntry;
                })
                .collect(Collectors.toList());
        result.setDataEntry(dataEntries);

        result.setFeatures(dsFeatures);

       return dataset;
    }

}
