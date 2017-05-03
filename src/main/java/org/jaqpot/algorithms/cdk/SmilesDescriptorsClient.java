package org.jaqpot.algorithms.cdk;

import org.jaqpot.algorithms.dto.dataset.Dataset;
import org.openscience.cdk.exception.CDKException;

public interface SmilesDescriptorsClient {

    Boolean isSmilesDocument(Byte[] smilesFile);

    Dataset generateDatasetBySmiles(String[] wantedCategories, byte[] smilesFile) throws CDKException;

}

