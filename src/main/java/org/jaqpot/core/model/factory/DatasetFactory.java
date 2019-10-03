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
package org.jaqpot.core.model.factory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.inject.Inject;
import org.jaqpot.core.model.builder.MetaInfoBuilder;
import org.jaqpot.core.model.DataEntry;
import org.jaqpot.core.model.dto.dataset.Dataset;
import org.jaqpot.core.model.dto.dataset.FeatureInfo;
import org.jaqpot.core.model.dto.dataset.EntryId;
import org.jaqpot.core.model.util.ROG;
import org.jaqpot.core.properties.PropertyManager;

/**
 *
 * @author Charalampos Chomenidis
 * @author Pantelis Sopasakis
 */
public class DatasetFactory {


    public static Dataset createEmpty(Integer rows) {
        Dataset dataset = new Dataset();
        List<DataEntry> dataEntries = IntStream.range(1, rows + 1)
                .mapToObj(i -> {
                    DataEntry de = new DataEntry();
                    de.setValues(new TreeMap<>());
                    EntryId s = new EntryId();
                    s.setName(Integer.toString(i));
                    s.setURI("/entryId/" + i);
                    de.setEntryId(s);
                    return de;
                }).collect(Collectors.toList());
        dataset.setDataEntry(dataEntries);
        dataset.setId(UUID.randomUUID().toString());
        dataset.setVisible(Boolean.TRUE);
        ROG randomStringGenerator = new ROG(true);
        dataset.setId(randomStringGenerator.nextString(14));
        dataset.setFeatures(new HashSet<>());
        dataset.setMeta(MetaInfoBuilder.builder()
                .addTitles("Empty dataset")
                .addDescriptions("Empty dataset")
                .addCreators(new String[0])
                .build());

        return dataset;
    }

    public static Dataset create(List content, String featureTemplate) {

        int rows = content.size();
        int numFeatures = 1;
        //int numColumns = numFeatures;
        Dataset dataset = new Dataset();

        List<DataEntry> dataEntries = IntStream.range(0, rows)
                .mapToObj((int i) -> {
                    DataEntry de = new DataEntry();
                    TreeMap values = new TreeMap<>();
                    values.put(Integer.toString(numFeatures - 1), content.get(i));
                    de.setValues(values);
                    EntryId s = new EntryId();
                    s.setName(Integer.toString(i));
                    s.setURI("/entryId/" + i);
                    de.setEntryId(s);
                    return de;
                }).collect(Collectors.toList());

        dataset.setDataEntry(dataEntries);
        dataset.setId(UUID.randomUUID().toString());
        dataset.setVisible(Boolean.TRUE);
        ROG randomStringGenerator = new ROG(true);
        dataset.setId(randomStringGenerator.nextString(14));
        FeatureInfo fi = new FeatureInfo();
        fi.setKey(Integer.toString(numFeatures - 1));
        fi.setName(featureTemplate.split("feature/")[1]);
        fi.setURI(featureTemplate + "/" + randomStringGenerator.nextString(14));
        Set<FeatureInfo> features = new HashSet();
        features.add(fi);
        dataset.setFeatures(features);
        dataset.setMeta(MetaInfoBuilder.builder()
                .addTitles("Empty dataset")
                .addDescriptions("Empty dataset")
                .addCreators(new String[0])
                .build());

        return dataset;
    }

    public static void addEmptyRows(Dataset dataset, Integer rows) {
        List<DataEntry> dataEntries = IntStream.range(1, rows + 1)
                .mapToObj(i -> {
                    DataEntry de = new DataEntry();
                    de.setValues(new TreeMap<>());
                    EntryId s = new EntryId();
                    s.setName(Integer.toString(i));
                    s.setURI("/entryId/" + i);
                    de.setEntryId(s);
                    return de;
                }).collect(Collectors.toList());
        dataset.setDataEntry(dataEntries);
    }

    public static Dataset copy(Dataset dataset) {
        Dataset result = new Dataset();
        result.setId(dataset.getId());
        result.setMeta(dataset.getMeta());

        List<DataEntry> dataEntries = dataset.getDataEntry()
                .parallelStream()
                .map(dataEntry -> {
                    DataEntry newEntry = new DataEntry();
                    newEntry.setEntryId(dataEntry.getEntryId());
                    newEntry.setValues(new TreeMap<>(dataEntry.getValues()));
                    return newEntry;
                })
                .collect(Collectors.toList());
        result.setDataEntry(dataEntries);
        result.setFeatures(dataset.getFeatures());
        result.setDatasetURI(dataset.getDatasetURI());
        result.setDescriptors(dataset.getDescriptors());
        result.setTotalColumns(dataset.getTotalColumns());
        result.setTotalRows(dataset.getTotalRows());
        result.setByModel(dataset.getByModel());
        return result;
    }

    public static Dataset copy(Dataset dataset, Integer rowStart, Integer rowMax) {
        Dataset result = new Dataset();
        result.setId(UUID.randomUUID().toString());
        result.setMeta(dataset.getMeta());

        List<DataEntry> dataEntries = dataset.getDataEntry()
                .parallelStream()
                .skip(rowStart)
                .limit(rowMax)
                .map(dataEntry -> {
                    DataEntry newEntry = new DataEntry();
                    newEntry.setEntryId(dataEntry.getEntryId());
                    newEntry.setValues(new TreeMap<>(dataEntry.getValues()));
                    return newEntry;
                })
                .collect(Collectors.toList());
        result.setDataEntry(dataEntries);
        result.setFeatures(dataset.getFeatures());
        result.setDatasetURI(dataset.getDatasetURI());
        result.setDescriptors(dataset.getDescriptors());
        result.setTotalColumns(dataset.getTotalColumns());
        result.setTotalRows(dataset.getTotalRows());
        result.setByModel(dataset.getByModel());
        return result;
    }

    public static Dataset select(Dataset dataset, HashSet<String> features) {
        Dataset result = new Dataset();
        result.setId(dataset.getId());
        result.setMeta(dataset.getMeta());
        //Integer num_features = features.size();
        //ref: https://stackoverflow.com/questions/24010109/java-8-stream-reverse-order
        //List<Integer> index = IntStream.range(0, num_features-1).map(i->-i+num_features-1).boxed().collect(Collectors.toList());
        //Stack index_stack = new Stack();
        //index_stack.addAll(index);
        Set<String> keyValues = new HashSet();

        Set<FeatureInfo> ds_features = dataset.getFeatures();
        ds_features.stream().forEach(feature -> {
            if (features.contains(feature.getURI())) {
                keyValues.add(feature.getKey());
            }
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
                    //.filter(feature -> features.contains(feature))
                    .filter(key -> keyValues.contains(key))
                    .forEach(key -> {

                        values.put(key, dataEntry.getValues().get(key));

                    });
                    newEntry.setValues(values);
                    return newEntry;
                })
                .collect(Collectors.toList());
        result.setDataEntry(dataEntries);
        Set<FeatureInfo> featureInfo = new HashSet<>();
        dataset.getFeatures().stream()
                .filter(f -> keyValues.contains(f.getKey()))
                .forEach(f -> featureInfo.add(f));
        result.setFeatures(featureInfo);
        return result;
    }

    public static Dataset mergeColumns(Dataset dataset, Dataset other) {
        if (dataset != null && other == null) {
            return dataset;
        } else if (dataset == null && other != null) {
            return other;
        } else if (dataset == null && other == null) {
            return null;
        } else {
            for (int i = 0; i < Math.min(dataset.getDataEntry().size(), other.getDataEntry().size()); i++) {
                DataEntry dataEntry = dataset.getDataEntry().get(i);
                DataEntry otherEntry = other.getDataEntry().get(i);
                dataEntry.getValues().putAll(otherEntry.getValues());
            }
            dataset.getFeatures().addAll(other.getFeatures());
            return dataset;
        }
    }

    public static Dataset mergeRows(Dataset dataset, Dataset other) {
        if (dataset != null && other == null) {
            return dataset;
        } else if (dataset == null && other != null) {
            return other;
        } else if (dataset == null && other == null) {
            return null;
        } else {
            dataset.getDataEntry().addAll(other.getDataEntry());
            dataset.getFeatures().addAll(other.getFeatures());
            return dataset;
        }
    }

    public static Dataset randomize(Dataset dataset, Long seed) {
        Random generator = new Random(seed);
        dataset.setDataEntry(generator.ints(dataset.getDataEntry().size(), 0, dataset.getDataEntry().size())
                .mapToObj(i -> {
                    return dataset.getDataEntry().get(i);
                })
                .collect(Collectors.toList()));
        return dataset;
    }

    public static Dataset stratify(Dataset dataset, Integer folds, String targetFeature) {
        Object value = dataset.getDataEntry().get(0).getValues().get(targetFeature);
        if (value instanceof Number) {
            List<DataEntry> sortedEntries = dataset.getDataEntry().stream()
                    .sorted((e1, e2) -> {
                        Double a = Double.parseDouble(e1.getValues().get(targetFeature).toString());
                        Double b = Double.parseDouble(e2.getValues().get(targetFeature).toString());
                        return a.compareTo(b);
                    })
                    .collect(Collectors.toList());

            List<DataEntry> finalEntries = new ArrayList<>();
            int i = 0;
            while (finalEntries.size() < sortedEntries.size()) {
                int k = 0, j = 0;
                while (k < sortedEntries.size()) {
                    k = i + j * folds;
                    if (k >= sortedEntries.size()) {
                        break;
                    }
                    DataEntry de = sortedEntries.get(k);
                    finalEntries.add(de);
                    j++;
                }
                i++;
            }
            dataset.setDataEntry(finalEntries);
            return dataset;
        } else {
            return null;
        }

    }

    public static Dataset addNullFeaturesFromPretrained(Dataset dataset, List<String> independentFeatures, List<String> predictedFeatures) {
        DataEntry de = new DataEntry();
        EntryId entryId = new EntryId();
        entryId.setOwnerUUID(dataset.getMeta().getCreators().toArray()[0].toString());
        de.setEntryId(entryId);

        TreeMap<String, Object> values = new TreeMap();
        independentFeatures.forEach((indFeat) -> {
            values.put(indFeat, null);
        });
        predictedFeatures.forEach(predFeat -> {
            values.put(predFeat, null);
        });
        de.setValues(values);
        dataset.getDataEntry().add(de);

        return dataset;
    }
}
