package org.jaqpot.algorithms.utils;

import org.apache.commons.lang3.math.NumberUtils;
import org.jaqpot.algorithms.dto.dataset.DataEntry;
import org.jaqpot.algorithms.dto.dataset.Dataset;
import org.jaqpot.algorithms.dto.dataset.FeatureInfo;
import org.jaqpot.algorithms.dto.dataset.Substance;

import java.io.InputStream;
import java.util.*;

import static org.jaqpot.algorithms.utils.CSVUtils.parseLine;

public class DatasetFactory {
    public static Dataset mergeRows(Dataset dataset, Dataset other) {
        if (dataset != null && other == null) {
            return dataset;
        } else if (dataset == null && other != null) {
            return other;
        } else if (dataset == null && other == null) {
            return null;
        } else {
            for (int i = 0; i < dataset.getDataEntry().size(); i++)
                dataset.getDataEntry().get(i).getValues().putAll(other.getDataEntry().get(i).getValues());
            dataset.getFeatures().addAll(other.getFeatures());
            return dataset;
        }
    }

    public static  Dataset calculateRowsAndColumns(InputStream stream) {
        Dataset dataset = new Dataset();
        Scanner scanner = new Scanner(stream);

        Set<FeatureInfo> featureInfoList = new HashSet<>();
        List<DataEntry> dataEntryList = new ArrayList<>();
        List<String> feature = new LinkedList<>();
        boolean firstLine = true;
        int count = 0;
        while (scanner.hasNext()) {

            List<String> line = parseLine(scanner.nextLine());
            if (firstLine) {
                for (String l : line) {
                    String pseudoURL = ("/feature/" + l).replaceAll(" ","_"); //uriInfo.getBaseUri().toString()+
                    feature.add(pseudoURL);
                    featureInfoList.add(new FeatureInfo(pseudoURL, l,"NA",new HashMap<>(),Dataset.DescriptorCategory.EXPERIMENTAL));
                }
                firstLine = false;
            } else {
                Iterator<String> it1 = feature.iterator();
                Iterator<String> it2 = line.iterator();
                TreeMap<String, Object> values = new TreeMap<>();
                while (it1.hasNext() && it2.hasNext()) {
                    String it = it2.next();
                    if (!NumberUtils.isParsable(it))
                        values.put(it1.next(), it);
                    else
                        values.put(it1.next(),Float.parseFloat(it));
                }
                Substance substance = new Substance();
                substance.setURI("/substance/" + count);
                substance.setName("row" + count);
                substance.setOwnerUUID("7da545dd-2544-43b0-b834-9ec02553f7f2");
                DataEntry dataEntry = new DataEntry();
                dataEntry.setValues(values);
                dataEntry.setCompound(substance);
                dataEntryList.add(dataEntry);
            }
            count++;
        }
        scanner.close();
        dataset.setFeatures(featureInfoList);
        dataset.setDataEntry(dataEntryList);
        return dataset;
    }
}
