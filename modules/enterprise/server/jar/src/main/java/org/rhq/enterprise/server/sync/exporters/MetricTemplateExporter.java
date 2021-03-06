/*
 * RHQ Management Platform
 * Copyright (C) 2005-2011 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.rhq.enterprise.server.sync.exporters;

import java.util.Iterator;
import java.util.List;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.criteria.MeasurementDefinitionCriteria;
import org.rhq.core.domain.measurement.MeasurementDefinition;
import org.rhq.core.domain.sync.entity.MetricTemplate;
import org.rhq.core.domain.util.PageControl;
import org.rhq.enterprise.server.measurement.MeasurementDefinitionManagerLocal;
import org.rhq.enterprise.server.util.LookupUtil;

/**
 * 
 *
 * @author Lukas Krejci
 */
public class MetricTemplateExporter implements Exporter<MeasurementDefinition, MetricTemplate> {

    private static class MetricTemplateIterator extends JAXBExportingIterator<MetricTemplate, MeasurementDefinition> {

        public MetricTemplateIterator(Iterator<MeasurementDefinition> sourceIterator) {
            super(sourceIterator, MetricTemplate.class);
        }

        @Override
        public String getNotes() {
            return null;
        }

        @Override
        protected MetricTemplate convert(MeasurementDefinition object) {
            return new MetricTemplate(object);
        }

    }

    private MeasurementDefinitionManagerLocal measurementDefinitionManager;
    private Subject subject;

    public MetricTemplateExporter(Subject subject, MeasurementDefinitionManagerLocal measurementDefinitionManager) {
        this.subject = subject;
        this.measurementDefinitionManager = measurementDefinitionManager;
    }

    @Override
    public ExportingIterator<MetricTemplate> getExportingIterator() {
        MeasurementDefinitionCriteria criteria = new MeasurementDefinitionCriteria();
        criteria.setPageControl(PageControl.getUnlimitedInstance());
        criteria.fetchResourceType(true);
        
        List<MeasurementDefinition> defs = measurementDefinitionManager.findMeasurementDefinitionsByCriteria(subject,
            criteria);
        
        return new MetricTemplateIterator(defs.iterator());
    }

    @Override
    public String getNotes() {
        return null;
    }

}
