package org.exoplatform.salesforce.service.jpa;

import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.commons.persistence.impl.GenericDAOJPAImpl;
import org.exoplatform.salesforce.PostActivitiesEntity;
import org.exoplatform.salesforce.dao.PostActivitiesHandler;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.logging.Logger;

/**
 * Created by bechir on 06/08/15.
 */
public class PostActivitiesDAOImpl extends GenericDAOJPAImpl<PostActivitiesEntity, Long> implements PostActivitiesHandler {

    private static final Logger LOG = Logger.getLogger("PostActivitiesDAOImpl");

    private EntityManagerService entityService;

    public PostActivitiesDAOImpl(EntityManagerService entityService) {
        this.entityService = entityService;
    }

    @Override
    public EntityManager getEntityManager() {
        return entityService.getEntityManager();
    }

    @Override
    public PostActivitiesEntity findPost(String postId) {
        EntityManager em = getEntityManager();
        Query query = em.createNamedQuery("PostActivitiesEntity.findPost");
        query.setParameter("postId", postId);

        return (PostActivitiesEntity) query.getSingleResult();
    }

    @Override
    public PostActivitiesEntity findActivity(String activityId) {
        EntityManager em = getEntityManager();
        Query query = em.createNamedQuery("PostActivitiesEntity.findActivity");
        query.setParameter("activityId", activityId);

        return (PostActivitiesEntity) query.getSingleResult();
    }
}