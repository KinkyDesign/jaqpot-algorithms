package org.jaqpot.algorithms.resource;


import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.jaqpot.algorithms.cdk.SmilesDescriptorsClientImpl;
import org.jaqpot.algorithms.dto.dataset.DataEntry;
import org.jaqpot.algorithms.dto.dataset.Dataset;
import org.jaqpot.algorithms.dto.jpdi.CalculateRequest;
import org.jaqpot.algorithms.dto.jpdi.CalculateResponse;
import org.jaqpot.algorithms.utils.DatasetFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Created by Angelos Valsamis on 4/4/2017.
 */

@Path("smiles")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SmilesDescriptor {
    private static final Logger LOG = Logger.getLogger(SmilesDescriptor.class.getName());

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("calculate")
    public Response calculate(
            CalculateRequest calculateRequest) {
        try {
            ArrayList<String> _categories =new ArrayList<>();
            _categories.add("all");

            Map<String, Object> parameters = calculateRequest.getParameters() != null ? calculateRequest.getParameters() : new HashMap<>();
            ArrayList<String> wantedCategoriesArray = (ArrayList<String>)parameters.getOrDefault("categories", _categories);

            String[] wantedCategories = wantedCategoriesArray.toArray(new String[0]);

            byte[] bFile = calculateRequest.getFile();
            if (bFile == null) return Response.status(Response.Status.BAD_REQUEST).entity("Empty File").build();


            //Get first column
            CsvParserSettings settings = new CsvParserSettings();
            settings.selectIndexes(0);
            settings.setHeaderExtractionEnabled(true);

            CsvParser parser = new CsvParser(settings);

            List<String> firstColumn = new ArrayList<>();
            List<String[]> allRows = parser.parseAll(new ByteArrayInputStream(bFile));
            for (String[] row:allRows)
                firstColumn.add(row[0]);

            //Calculate smiles features
            SmilesDescriptorsClientImpl smilesDescriptorsClient = new SmilesDescriptorsClientImpl();
            Dataset calculations = smilesDescriptorsClient.generateDatasetBySmiles(wantedCategories,firstColumn);

            calculations.getDataEntry().forEach(de -> {
                calculations.getDataEntry().stream()
                            .filter(e -> !e.equals(de))
                            .forEach(e -> {
                                de.getValues().keySet().retainAll(e.getValues().keySet());
                            });
                    if (!calculations.getDataEntry().isEmpty()) {
                        calculations.setFeatures(calculations.getFeatures()
                                .stream()
                                .filter(f -> calculations.getDataEntry()
                                        .get(0)
                                        .getValues()
                                        .keySet()
                                        .contains(f.getURI())
                                )
                                .collect(Collectors.toSet()));
                    }
                });

            Set<Dataset.DescriptorCategory> descriptorCategories = new TreeSet<>();
            descriptorCategories.add(Dataset.DescriptorCategory.CDK);
            calculations.setDescriptors(descriptorCategories);

            //Calculate file features
            Dataset dummyDataset = DatasetFactory.calculateRowsAndColumns(new ByteArrayInputStream(bFile));

            Dataset finalDataset = DatasetFactory.mergeRows(dummyDataset,calculations);
            calculations.setTotalRows(calculations.getDataEntry().size());
            calculations.setTotalColumns(calculations.getDataEntry()
                    .stream()
                    .max((e1, e2) -> Integer.compare(e1.getValues().size(), e2.getValues().size()))
                    .orElseGet(() -> {
                        DataEntry de = new DataEntry();
                        de.setValues(new TreeMap<>());
                        return de;
                    })
                    .getValues().size());
            CalculateResponse calculateResponse = new CalculateResponse();
            calculateResponse.setEntries(finalDataset);
            return Response.ok(calculateResponse).build();
        } catch (Exception ex) {
        LOG.log(Level.SEVERE, null, ex);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
        }
    }
}