package org.jaqpot.algorithms.cdk;

import org.jaqpot.algorithms.dto.dataset.*;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.iterator.DefaultIteratingChemObjectReader;
import org.openscience.cdk.io.iterator.IteratingSDFReader;
import org.openscience.cdk.qsar.DescriptorValue;
import org.openscience.cdk.qsar.IDescriptor;
import org.openscience.cdk.qsar.IMolecularDescriptor;
import org.openscience.cdk.qsar.result.*;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.smsd.tools.ExtAtomContainerManipulator;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DescriptorCalculator  {

    private List<IDescriptor> descriptors;

    private String inputFormat = "smi";
    private File tempFile;
    private List<ExceptionInfo> exceptionList;
    private int molCount = 0;
    private boolean canceled = false;
    private double elapsedTime;


    public DescriptorCalculator(List<IDescriptor> descriptors,
                                File tempFile) {
        this.descriptors = descriptors;
        this.tempFile = tempFile;
        exceptionList = new ArrayList<ExceptionInfo>();

        if (inputFormat.equals("invalid")) {
            canceled = true;
            JOptionPane.showMessageDialog(null,
                    "Input file format was not recognized. It should be SDF or SMI" +
                            "\nYou should avoid supplying Markush structures since will be" +
                            "\nignored anyway",
                    "CDKDescUI Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public List<ExceptionInfo> getExceptionList() {
        return exceptionList;
    }

    public Dataset calculateMoleculeDescriptors(List<String> sdfFileName) throws CDKException, IOException {

        //Get a local instance, since we need to remove groups of NA descriptors
        List<IDescriptor> myDescriptors = this.descriptors;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        for (String element : sdfFileName) {
            out.writeBytes(element);
            out.write(0x0A);
        }
        byte[] bytes  = baos.toByteArray();

        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);

        Dataset dataset = new Dataset();
        DefaultIteratingChemObjectReader iterReader = null;

        if (inputFormat.equals("smi"))
            iterReader = new MyIteratingSMILESReader(bis, SilentChemObjectBuilder.getInstance());
        else if (inputFormat.equals("mdl")) {
            iterReader = new IteratingSDFReader(bis, DefaultChemObjectBuilder.getInstance());
            ((IteratingSDFReader) iterReader).customizeJob();
        }

        molCount = 0;

        // lets get the header line first
        List<String> headerItems = new ArrayList<String>();
        headerItems.add("Title");



        System.out.println(Arrays.toString(headerItems.toArray(new String[]{})));

        elapsedTime = System.currentTimeMillis();
        assert iterReader != null;
        List<DataEntry> dataEntries = new ArrayList<>();
        while (iterReader.hasNext()) {
            //Create data entries for dataset
            if (canceled) return null;
            TreeMap<String, Object> calulationMap = new TreeMap<>();

            IAtomContainer molecule = (IAtomContainer) iterReader.next();



            String title = (String) molecule.getProperty(CDKConstants.SMILES);

            if (title == null) title = "Mol" + String.valueOf(molCount + 1);


            try {
                molecule= ExtAtomContainerManipulator.checkAndCleanMolecule(molecule);
            } catch (Exception e) {
                exceptionList.add(new ExceptionInfo(molCount + 1, molecule, e, ""));
                molCount++;
                continue;
            }

            // OK, we can now eval the descriptors
            Iterator<IDescriptor> iter = myDescriptors.iterator();
            while (iter.hasNext()) {

                Object object = iter.next();

                if (canceled) return null;
                IMolecularDescriptor descriptor = (IMolecularDescriptor) object;
                String[] comps = descriptor.getSpecification().getSpecificationReference().split("#");

                DescriptorValue value = descriptor.calculate(molecule);
                if (value.getException() != null) {
                    exceptionList.add(new ExceptionInfo(molCount + 1, molecule, value.getException(), comps[1]));
                    //if descriptor not applicable for a certain molecule, remove that descriptor from checked descriptors and from past entries
                    iter.remove();
                    //for (String value1:value.getNames())
                    //    if (dataset.getDataEntry() != null)
                    //        dataset.getDataEntry().stream().filter(dataEntry -> dataEntry.getValues().containsKey(value1)).forEach(dataEntry -> dataEntry.getValues().remove(value1));
                    continue;
                }

                IDescriptorResult result = value.getValue();
                if (result instanceof DoubleResult) {
                    if (!String.valueOf(((DoubleResult) result)).equals("NaN") && !String.valueOf(((DoubleResult) result)).equals("NA"))
                        calulationMap.put("/jaqpot/feature/" + value.getNames()[0], ((DoubleResult) result).doubleValue());
                }
                else if (result instanceof IntegerResult) {
                    if (!String.valueOf(((IntegerResult) result)).equals("NaN") && !String.valueOf(((IntegerResult) result)).equals("NA"))
                        calulationMap.put("/jaqpot/feature/" + value.getNames()[0], ((IntegerResult) result).intValue());
                }
                else if (result instanceof DoubleArrayResult)
                    for (int i = 0; i < result.length(); i++)
                    {
                        if (!String.valueOf(((DoubleArrayResult) result).get(i)).equals("NaN") && !String.valueOf(((DoubleArrayResult) result).get(i)).equals("NA"))
                            calulationMap.put("/jaqpot/feature/"+value.getNames()[i],((DoubleArrayResult) result).get(i));
                    }

                else if (result instanceof IntegerArrayResult)
                    for (int i = 0; i < result.length(); i++)
                    {
                        if (!String.valueOf(((IntegerArrayResult) result).get(i)).equals("NaN") && !String.valueOf(((IntegerArrayResult) result).get(i)).equals("NA"))
                            calulationMap.put("/jaqpot/feature/"+value.getNames()[i],((IntegerArrayResult) result).get(i));
                    }
            }

            DataEntry dataEntry = new DataEntry();
            EntryId entryId = new EntryId();
            entryId.setName(title);
            dataEntry.setEntryId(entryId);
            dataEntry.setValues(calulationMap);

            dataEntries.add(dataEntry);
            molCount++;
        }

        dataset.setDataEntry(dataEntries);

        //Create feature definitions for dataset
        Set<FeatureInfo> features = new HashSet<>();
        for (IDescriptor descriptor : myDescriptors) {
            String[] names = descriptor.getDescriptorNames();
            headerItems.addAll(Arrays.asList(names));
            //Create features for dataset
            for (String name : descriptor.getDescriptorNames()) {
                FeatureInfo featureInfo = new FeatureInfo();
                HashMap<String,Object> conditions = new HashMap<>();
                conditions.put("Implementation Identifier",descriptor.getSpecification().getImplementationIdentifier());
                conditions.put("Implementation Title",descriptor.getSpecification().getImplementationTitle());
                conditions.put("Implementation Vendor",descriptor.getSpecification().getImplementationVendor());
                conditions.put("Specification Reference",descriptor.getSpecification().getSpecificationReference());
                featureInfo.setConditions(conditions);
                featureInfo.setName(name);
                featureInfo.setCategory(Dataset.DescriptorCategory.CDK);
                featureInfo.setURI("/jaqpot/feature/"+name);
                features.add(featureInfo);
            }
            dataset.setFeatures(features);
        }


        // calculation is done, lets eval the elapsed time
        elapsedTime = ((System.currentTimeMillis() - elapsedTime) / 1000.0);
        System.out.println("Elapsed Time:"+ elapsedTime);
        try {
            iterReader.close();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error closing output files",
                    "CDKDescUI Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return dataset;
    }
}
