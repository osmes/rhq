/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2, as
 * published by the Free Software Foundation, and/or the GNU Lesser
 * General Public License, version 2.1, also as published by the Free
 * Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License and the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and the GNU Lesser General Public License along with this program;
 * if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package org.rhq.enterprise.server.util;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Hibernate;

import org.rhq.core.domain.criteria.Criteria;
import org.rhq.core.domain.criteria.Criteria.Restriction;
import org.rhq.core.domain.util.PageControl;
import org.rhq.core.domain.util.PageList;

public class CriteriaQueryRunner<T> {

    private static final Log LOG = LogFactory.getLog(CriteriaQueryRunner.class);

    private Criteria criteria;
    private CriteriaQueryGenerator queryGenerator;
    private EntityManager entityManager;
    private boolean automaticFetching;

    public CriteriaQueryRunner(Criteria criteria, CriteriaQueryGenerator queryGenerator, EntityManager entityManager) {
        this(criteria, queryGenerator, entityManager, true);
    }

    public CriteriaQueryRunner(Criteria criteria, CriteriaQueryGenerator queryGenerator, EntityManager entityManager,
        boolean automaticFetching) {
        this.criteria = criteria;
        this.queryGenerator = queryGenerator;
        this.entityManager = entityManager;
        this.automaticFetching = automaticFetching;
    }

    private static final int MAX_ATTEMPTS = 10;
    private static final float START_WAIT_TIME = 100;
    private static final float MAX_WAIT_TIME = 1000;

    //we increase the wait time in a geometric progression from START_WAIT_TIME to MAX_WAIT_TIME...
    private static final float INCREASE_COEFF = (float) Math
        .pow(MAX_WAIT_TIME / START_WAIT_TIME, 1d / (MAX_ATTEMPTS - 1));

    private class CollectionAndCountFetch {
        Collection<? extends T> collection;
        int count;

        void fetch(PageControl pageControl) {
            collection = getCollection();
            count = getCount();
            int cnt = 0;
            float waitTime = 100;

            long time = System.currentTimeMillis();

            while (!pageControl.isConsistentWith(collection, count) &&
                ++cnt < MAX_ATTEMPTS) { //++cnt - we already made 1 attempt out of the loop

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Possible phantom read detected while running a criteria query. The collection size = " +
                        collection.size() + ", count = " + count + ", pageControl = " + pageControl +
                        ". Attempt number " + cnt + ". Will wait for " + ((int) waitTime) + "ms.", new Exception());
                }

                try {
                    Thread.sleep((int) waitTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                collection = getCollection();
                count = getCount();

                waitTime *= INCREASE_COEFF;
            }

            if (cnt == MAX_ATTEMPTS) {
                time = System.currentTimeMillis() - time;
                LOG.warn(
                    "Could not get consistent results of the paged data and a total count for " +
                        CriteriaUtil.toString(criteria) + ". After " + MAX_ATTEMPTS + " attempts, the collection size" +
                        " is " + collection.size() + ", while the count query reports " + count + " for " +
                        pageControl + ". The discrepancy has not cleared up in " + time + "ms so we're giving up, " +
                        "returning inconsistent results. Note that is most possibly NOT an error. It is likely " +
                        "caused by concurrent database activity that changes the contents of the database that the " +
                        "criteria query is querying.", new Exception());
            }
        }
    }

    public PageList<T> execute() {
        PageList<T> results;
        PageControl pageControl = CriteriaQueryGenerator.getPageControl(criteria);

        Restriction criteriaRestriction = criteria.getRestriction();
        if (criteriaRestriction == null) {
            CollectionAndCountFetch fetch = new CollectionAndCountFetch();
            fetch.fetch(pageControl);
            results = new PageList<T>(fetch.collection, fetch.count, pageControl);
//            if (LOG.isDebugEnabled()) {
//                LOG.debug("restriction=" + criteriaRestriction + ", resultSize=" + results.size() + ", resultCount="
//                    + results.getTotalSize());
//            }

        } else if (criteriaRestriction == Restriction.COUNT_ONLY) {
            results = new PageList<T>(getCount(), pageControl);
//            if (LOG.isDebugEnabled()) {
//                LOG.debug("restriction=" + criteriaRestriction + ", resultCount=" + results.getTotalSize());
//            }

        } else if (criteriaRestriction == Restriction.COLLECTION_ONLY) {
            results = new PageList<T>(getCollection(), pageControl);
//            if (LOG.isDebugEnabled()) {
//                LOG.debug("restriction=" + criteriaRestriction + ", resultSize=" + results.size());
//            }

        } else {
            throw new IllegalArgumentException(this.getClass().getSimpleName()
                + " does not support query execution for criteria with " + Restriction.class.getSimpleName() + " "
                + criteriaRestriction);
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private Collection<? extends T> getCollection() {
        Query query = queryGenerator.getQuery(entityManager);
        List<T> results = query.getResultList();

        /* 
         * suppression of auto-fetch useful in cases where alterProject(String) was called on the generator, which
         * changed the return type of the result set from List<T> to something else.  in that case, the caller to
         * this method must, as necessary, perform the fetch manually.
         */
        if (automaticFetching) {
            if (!queryGenerator.getPersistentBagFields().isEmpty()) {
                for (T entity : results) {
                    initPersistentBags(entity);
                }
            }
            if (!queryGenerator.getJoinFetchFields().isEmpty()) {
                for (T entity : results) {
                    initJoinFetchFields(entity);
                }
            }
        }

        return results;
    }

    private int getCount() {
        Query countQuery = queryGenerator.getCountQuery(entityManager);
        long count = (Long) countQuery.getSingleResult();

        return (int) count;
    }

    public void initFetchFields(Object entity) {
        initPersistentBags(entity);
        initJoinFetchFields(entity);
    }

    private void initPersistentBags(Object entity) {
        for (Field persistentBagField : queryGenerator.getPersistentBagFields()) {
            initialize(entity, persistentBagField);
        }
    }

    private void initJoinFetchFields(Object entity) {
        for (Field joinFetchField : queryGenerator.getJoinFetchFields()) {
            initialize(entity, joinFetchField);
        }
    }

    private void initialize(Object entity, Field field) {
        try {
            field.setAccessible(true);

            Object instance = field.get(entity);

            Hibernate.initialize(instance);

            if (instance instanceof Iterable) {
                Iterator<?> it = ((Iterable<?>) instance).iterator();
                while (it.hasNext()) it.next();
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Could not initialize " + field + "  Following exception has caused the problem: ", e);
            } else {
                LOG.warn("Could not initialize " + field);
            }
        }
    }

}
