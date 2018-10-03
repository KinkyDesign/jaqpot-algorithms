/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jaqpot.core.service.resource;

import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.jaxrs.Reader;
import io.swagger.jaxrs.config.ReaderListener;
import io.swagger.models.Swagger;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.In;
import javax.enterprise.context.ApplicationScoped;

/**
 *
 * @author hampos
 */
@SwaggerDefinition(
        info = @Info(
                title = "Jaqpot API",
                description = "Jaqpot v4 (Quattro) is the 4t"
                        + "h version of a YAQP, a RESTful web platform which can"
                        + " be used to train machine learning models and use "
                        + "them to obtain toxicological predictions for given "
                        + "chemical compounds or engineered nano materials. "
                        + "Jaqpot v4 has integrated read-across, optimal experimental design,"
                        + " interlaboratory comparison, biokinetics and dose response modelling"
                        + " functionalities. The project is developed in Java8 and JEE7 by the"
                        + " <a href=\"http://www.chemeng.ntua.gr/labs/control_lab/\"> Unit of"
                        + " Process Control"
                        + " and Informatics in the School of Chemical Engineering </a> at"
                        + " the  <a href=\"https://www.ntua.gr/en/\"> National"
                        + " Technical University of Athens.</a> ",
                version = "4.0.3",
                contact = @Contact(name = "Charalampos Chomenidis,"
                        + " Pantelis Sopasakis, Evangelia Anagnostopoulou, Angelos Valsamis,"
                        + " George Drakakis, Pantelis Karatzas, Georgia Tsiliki, Philip Doganis,"
                        + " Haralambos Sarimveis", email = "hampos@me.com",
                        url = "https://github.com/KinkyDesign/jaqpot-web/issues")
        )
)
public class SwaggerFilter implements ReaderListener {

    @Override
    public void beforeScan(Reader reader, Swagger swgr) {

    }

    @Override
    public void afterScan(Reader reader, Swagger swgr) {
        ApiKeyAuthDefinition apiKeyDefinition = new ApiKeyAuthDefinition();
        apiKeyDefinition.setName("Authorization");
        apiKeyDefinition.setIn(In.HEADER);
        apiKeyDefinition.setType("apiKey");
        swgr.addSecurityDefinition("apiKey", apiKeyDefinition);
    }

}