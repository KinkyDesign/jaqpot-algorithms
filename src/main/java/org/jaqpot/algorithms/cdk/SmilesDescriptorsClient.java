package org.jaqpot.algorithms.cdk;

import org.jaqpot.algorithms.dto.dataset.Dataset;
import org.openscience.cdk.exception.CDKException;

import java.util.List;

public interface SmilesDescriptorsClient {

    Boolean isSmilesDocument(Byte[] smilesFile);

    Dataset generateDatasetBySmiles(String[] wantedCategories, List<String> smilesFile) throws CDKException;

}

