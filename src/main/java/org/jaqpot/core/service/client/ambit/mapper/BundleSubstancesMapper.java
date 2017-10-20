package org.jaqpot.core.service.client.ambit.mapper;

import org.jaqpot.core.model.dto.bundle.BundleSubstances;
import org.jaqpot.core.model.dto.dataset.Substance;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import javax.persistence.ManyToMany;
import java.util.List;

/**
 * Created by Angelos Valsamis on 12/12/2016.
 */

@Mapper
public interface BundleSubstancesMapper {
    BundleSubstancesMapper INSTANCE = Mappers.getMapper( BundleSubstancesMapper.class );

    BundleSubstances bundleSubstancesToBundleSubstances (org.jaqpot.ambitclient.model.dto.bundle.BundleSubstances bundleSubstances);

    List<Substance> substancesToSubstances(List<org.jaqpot.ambitclient.model.dataset.Substance> substance);

    Substance substanceToSubstance(org.jaqpot.ambitclient.model.dataset.Substance substance);

}
