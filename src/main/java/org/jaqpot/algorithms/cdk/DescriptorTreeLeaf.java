package org.jaqpot.algorithms.cdk;


import org.openscience.cdk.IImplementationSpecification;
import org.openscience.cdk.qsar.DescriptorSpecification;
import org.openscience.cdk.qsar.IDescriptor;

/**
 * credits to @author Rajarshi Guha
 */
public class DescriptorTreeLeaf implements Comparable {
    private IDescriptor instance;
    private IImplementationSpecification spec;
    private String definition;
    private String name;

    IImplementationSpecification getSpec() {
        return spec;
    }


    DescriptorTreeLeaf(IDescriptor instance, String definition) {
        this.instance = instance;
        this.spec = instance.getSpecification();

        String[] tmp = this.spec.getSpecificationReference().split("#");
        this.name = AppOptions.getEngine().getDictionaryTitle((DescriptorSpecification) spec);
        this.definition = definition;
    }

    public IDescriptor getInstance() {
        return instance;
    }

    public String getDefinition() {
        return definition;
    }

    String getName() {
        return name;
    }

    public boolean equals(DescriptorTreeLeaf aLeaf) {
        return name.equals(aLeaf.getName());
    }

    public String toString() {
        return name;
    }

    public int compareTo(Object o) throws ClassCastException {
        if (!(o instanceof DescriptorTreeLeaf)) throw new ClassCastException("Expected a DescriptorTreeLeaf");
        DescriptorTreeLeaf aLeaf = (DescriptorTreeLeaf) o;
        return this.name.compareTo(aLeaf.getName());
    }
}
