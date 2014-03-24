package uk.ac.susx.tag.classificationframework.featureextraction.pipelines.confighandlers;

/*
 * #%L
 * ConfigHandlerRemoveStopwords.java - classificationframework - CASM Consulting - 2,013
 * %%
 * Copyright (C) 2013 - 2014 CASM Consulting
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import uk.ac.susx.tag.classificationframework.featureextraction.filtering.TokenFilterRelevanceStopwords;
import uk.ac.susx.tag.classificationframework.featureextraction.pipelines.FeatureExtractionPipeline;
import uk.ac.susx.tag.classificationframework.featureextraction.pipelines.PipelineBuilder;

import java.util.List;

/**
 * If optionValue is true, then remove stopwords (that are particularly suited
 * to the relevance classification problem).
 *
 * User: Andrew D. Robertson
 * Date: 17/02/2014
 * Time: 18:22
 */
public class ConfigHandlerRemoveStopwords extends ConfigHandler<Boolean> {
    @Override
    public void handle(FeatureExtractionPipeline pipeline, Boolean optionValue, List<PipelineBuilder.Option> other) {
        if(optionValue) pipeline.add(new TokenFilterRelevanceStopwords(), getKey());
    }

    @Override
    public String getKey() {
        return "remove_stopwords";  //To change body of implemented methods use File | Settings | File Templates.
    }
}
