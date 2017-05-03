package org.jaqpot.algorithms.cdk;

import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.IImplementationSpecification;
import org.openscience.cdk.aromaticity.Aromaticity;
import org.openscience.cdk.aromaticity.ElectronDonation;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.graph.ConnectivityChecker;
import org.openscience.cdk.graph.Cycles;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomContainerSet;
import org.openscience.cdk.qsar.DescriptorSpecification;
import org.openscience.cdk.qsar.IDescriptor;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.AtomTypeAwareSaturationChecker;
import org.openscience.cdk.tools.CDKHydrogenAdder;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import static java.util.Collections.sort;

/**
 * credits to @author Rajarshi Guha
 */

public class CDKDescUtils {

    CDKDescUtils() {
    }

    /**
     * Checks whether the input file is in SMI format.
     * <p/>
     * The approach I take is to read the first line of the file. This should splittable to give two parts. The first
     * part should be a valid SMILES string. If so this method returns true, otherwise false
     *
     * @param filename The file to consider
     * @return true if the file is in SMI format, otherwise false
     */
    public static boolean isSMILESFormat(String filename) {
        String line1 = null;
        String line2 = null;
        try {
            BufferedReader in = new BufferedReader(new FileReader(filename));
            line1 = in.readLine();
            line2 = in.readLine();
            in.close();
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        assert line1 != null;

        String[] tokens = line1.split("\\s+");
        if (tokens.length == 0) return false;

        SmilesParser sp = new SmilesParser(DefaultChemObjectBuilder.getInstance());
        try {
            IAtomContainer m = sp.parseSmiles(tokens[0].trim());
        } catch (InvalidSmilesException ise) {
            return false;
        } catch (ArrayIndexOutOfBoundsException e) {
            return false;
        }

        // now check the second line
        // if there is no second line this probably a smiles
        // file
        if (line2 == null) return true;

        // ok we have a second line, so lets see if it's a smiles
        tokens = line2.split("\\s+");
        if (tokens.length == 0) return false;

        sp = new SmilesParser(DefaultChemObjectBuilder.getInstance());
        try {
            IAtomContainer m = sp.parseSmiles(tokens[0].trim());
        } catch (InvalidSmilesException ise) {
            return false;
        }
        catch (ArrayIndexOutOfBoundsException e) {
            return false;
        }
        return true;
    }

    public static  HashMap<String, DefaultMutableTreeNode>  instantiateCategories(){
        DefaultMutableTreeNode rootNode;
        rootNode = new DefaultMutableTreeNode("All Descriptors");

        DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);

        String[] availableClasses = AppOptions.getInstance().getEngine().getAvailableDictionaryClasses();
        if (AppOptions.getInstance().isDebug()) {
            for (String klass : availableClasses) System.err.println("DEBUG: descriptor class: " + klass);
        }

        // make a HashMap for the available classes
        HashMap<String, DefaultMutableTreeNode> level1Map = new HashMap<String, DefaultMutableTreeNode>();
        for (String dictClass : availableClasses) {
            String[] tmp = dictClass.split("Descriptor");
            level1Map.put(dictClass, addObject(null, tmp[0], rootNode, treeModel));
        }

        List descInst = AppOptions.getEngine().getDescriptorInstances();
        List<IImplementationSpecification> descSpec = AppOptions.getInstance().getEngine().getDescriptorSpecifications();
        int ndesc = descInst.size();

        if (AppOptions.getInstance().isDebug()) {
            System.err.println("DEBUG: Got " + ndesc + " descriptor instances");
            System.err.println("DEBUG: Got " + descSpec.size() + " descriptor specifications");
        }
        List leaves = new ArrayList();
        for (int i = 0; i < ndesc; i++) {
            DescriptorSpecification spec = (DescriptorSpecification) descSpec.get(i);
            String definition = AppOptions.getInstance().getEngine().getDictionaryDefinition(spec);

            if (definition == null || definition.equals(""))
                System.err.println("ERROR: " + spec.getImplementationTitle() + " had no definition!");

            if (AppOptions.getInstance().isDebug())
                System.err.println("DEBUG: Adding leaf for " + descSpec.get(i).getImplementationTitle());

            DescriptorTreeLeaf leaf = new DescriptorTreeLeaf((IDescriptor) descInst.get(i), definition);
            if (leaf.getName() == null || definition == null) {
                System.err.println("ERROR: " + leaf.getInstance() + " is missing an entry in the OWL dictionary");
                //throw new CDKException("Seems that " + leaf.getInstance() + " is missing an entry in the OWL dictionary");
            } else
                leaves.add(leaf);
        }
        sort(leaves);
        for (Object object : leaves) {
            DescriptorTreeLeaf aLeaf = (DescriptorTreeLeaf) object;
            IImplementationSpecification spec = aLeaf.getSpec();

            if (AppOptions.getInstance().isDebug())
                System.err.println("DEBUG: leaf spec = " + spec.getImplementationTitle());

            String[] dictClass = AppOptions.getInstance().getEngine().getDictionaryClass(spec);
            if (dictClass == null || dictClass.length == 0) {
                System.err.println("ERROR: " + spec.getImplementationIdentifier() + "(" + spec.getImplementationIdentifier() + ") : " + "Had no class entries in the dictionary!");
                continue;
            }
            DefaultMutableTreeNode parent = level1Map.get(dictClass[0]);
            addObject(parent, aLeaf, rootNode, treeModel);
        }
        return level1Map;
    }

    static IAtomContainer checkAndCleanMolecule(IAtomContainer molecule) throws CDKException {
        boolean isMarkush = false;
        for (IAtom atom : molecule.atoms()) {
            if (atom.getSymbol().equals("R")) {
                isMarkush = true;
                break;
            }
        }

        if (isMarkush) {
            throw new CDKException("Skipping Markush structure");
        }

        // Check for salts and such

        String title = molecule.getProperty(CDKConstants.TITLE);
        if (!ConnectivityChecker.isConnected(molecule)) {
            // lets see if we have just two parts if so, we assume its a salt and just work
            // on the larger part. Ideally we should have a check to ensure that the smaller
            //  part is a metal/halogen etc.
            IAtomContainerSet fragments = ConnectivityChecker.partitionIntoMolecules(molecule);
            if (fragments.getAtomContainerCount() > 2) {
                throw new CDKException("More than 2 components. Skipped");
            } else {
                IAtomContainer frag1 = fragments.getAtomContainer(0);
                IAtomContainer frag2 = fragments.getAtomContainer(1);
                if (frag1.getAtomCount() > frag2.getAtomCount()) molecule = frag1;
                else molecule = frag2;
                molecule.setProperty(CDKConstants.TITLE, title);
            }
        }

        // Do the configuration
        try {
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(molecule);
        } catch (CDKException e) {
            throw new CDKException("Error in atom typing" + e.toString());
        }

        CDKHydrogenAdder adder = CDKHydrogenAdder.getInstance(molecule.getBuilder());
        adder.addImplicitHydrogens(molecule);


        AtomTypeAwareSaturationChecker sat = new AtomTypeAwareSaturationChecker();
        sat.decideBondOrder(molecule);

        // add explicit H's if required
        if (AppOptions.getInstance().isAddH()) {
            AtomContainerManipulator.convertImplicitToExplicitHydrogens(molecule);
        }

        // do a aromaticity check
        try {
            Aromaticity aromaticity = new Aromaticity(ElectronDonation.daylight(),
                    Cycles.vertexShort());
            aromaticity.apply(molecule);
        } catch (CDKException e) {
            throw new CDKException("Error in aromaticity detection");
        }

        return molecule;
    }
    private static DefaultMutableTreeNode addObject(DefaultMutableTreeNode parent,
                                                    Object child,
                                                    DefaultMutableTreeNode rootNode,
                                                    DefaultTreeModel treeModel) {
        return addObject(parent, child, false, rootNode, treeModel);
    }

    private static DefaultMutableTreeNode addObject(DefaultMutableTreeNode parent,
                                                    Object child,
                                                    boolean shouldBeVisible,
                                                    DefaultMutableTreeNode rootNode,
                                                    DefaultTreeModel treeModel) {
        DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);

        if (parent == null) {
            parent = rootNode;
        }

        treeModel.insertNodeInto(childNode, parent,
                parent.getChildCount());

        //Make sure the user can see the lovely new node.
        if (shouldBeVisible) {
//            tree.scrollPathToVisible(new TreePath(childNode.getPath()));
        }
        return childNode;
    }


    public static Comparator getDescriptorComparator() {
        return new Comparator() {
            public int compare(Object o1, Object o2) {
                IDescriptor desc1 = (IDescriptor) o1;
                IDescriptor desc2 = (IDescriptor) o2;

                String[] comp1 = desc1.getSpecification().getSpecificationReference().split("#");
                String[] comp2 = desc2.getSpecification().getSpecificationReference().split("#");

                return comp1[1].compareTo(comp2[1]);
            }
        };
    }
}