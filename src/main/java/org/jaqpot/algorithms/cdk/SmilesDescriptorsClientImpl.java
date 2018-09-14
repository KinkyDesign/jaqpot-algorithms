package org.jaqpot.algorithms.cdk;

import org.jaqpot.algorithms.dto.dataset.Dataset;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.qsar.IDescriptor;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class SmilesDescriptorsClientImpl implements SmilesDescriptorsClient {
    @Override
    public Boolean isSmilesDocument(Byte[] smilesFile) {
        return null;
    }

    @Override
    public Dataset generateDatasetBySmiles(String[] wantedCategories, List<String> smilesFile) throws CDKException {
        HashMap<String, DefaultMutableTreeNode> categories;
        List<IDescriptor> selectedDescriptors = new ArrayList<IDescriptor>();
        categories = CDKDescUtils.instantiateCategories();
        Dataset dataEntries = null;
        Set<String> wantedUniqueCategories = new HashSet<>();

        for (String category: wantedCategories)
        {
            if (category.equals("all"))
                Collections.addAll(wantedUniqueCategories, "topological","hybrid","geometrical","constitutional","electronic");
            else
                wantedUniqueCategories.add(category);
        }

        for (String category:wantedUniqueCategories) {
            int selectedDescsCount = 0;
            if (categories.containsKey(category+"Descriptor")) {
                System.out.println("WantedDesc: "+categories.get(category+"Descriptor").getLeafCount());
                Enumeration<DefaultMutableTreeNode> defaultMutableTreeNodeEnumeration = categories.get(category+"Descriptor").depthFirstEnumeration();
                while (defaultMutableTreeNodeEnumeration.hasMoreElements()) {
                    DefaultMutableTreeNode defaultMutableTreeNode = defaultMutableTreeNodeEnumeration.nextElement();
                    if(defaultMutableTreeNode.isLeaf()) {
                        DescriptorTreeLeaf aLeaf = (DescriptorTreeLeaf) (defaultMutableTreeNode.getUserObject());
                        selectedDescriptors.add(aLeaf.getInstance());
                        selectedDescsCount++;
                    }
                }
                System.out.println("SelectedDesc: "+ selectedDescsCount);
            }
        }
        Collections.sort(selectedDescriptors, CDKDescUtils.getDescriptorComparator());

        DescriptorCalculator descriptorCalculator = new DescriptorCalculator(selectedDescriptors, new File("/home/agelos/Desktop/output3"));
        try {
            dataEntries = descriptorCalculator.calculateMoleculeDescriptors(smilesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return dataEntries;
    }
}
