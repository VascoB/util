/*
 * SafeOnline project.
 *
 * Copyright 2006-2007 Lin.k N.V. All rights reserved.
 * Lin.k N.V. proprietary/confidential. Use is subject to license terms.
 */

package net.link.util.jpa;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import net.link.util.jpa.annotation.QueryMethod;
import net.link.util.jpa.annotation.QueryParam;
import net.link.util.jpa.annotation.UpdateMethod;


/**
 * Invocation handler for the query object factory. The query object factory is using the Proxy API to construct the query object. The
 * behaviour of the query object is provided via this invocation handler.
 *
 * @author fcorneli
 */
public class QueryObjectInvocationHandler implements InvocationHandler {

    private final EntityManager entityManager;

    public QueryObjectInvocationHandler(EntityManager entityManager) {

        this.entityManager = entityManager;
    }

    public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {

        QueryMethod queryMethodAnnotation = method.getAnnotation( QueryMethod.class );
        if (null != queryMethodAnnotation)
            return query( queryMethodAnnotation, method, args );

        UpdateMethod updateMethodAnnotation = method.getAnnotation( UpdateMethod.class );
        if (null != updateMethodAnnotation)
            return update( updateMethodAnnotation, method, args );

        throw new RuntimeException( "@QueryMethod or @UpdateMethod annotation expected: " + method.getDeclaringClass().getName() );
    }

    private Object update(UpdateMethod updateMethodAnnotation, Method method, Object[] args) {

        String namedQueryName = updateMethodAnnotation.value();
        Query query = entityManager.createNamedQuery( namedQueryName );
        setParameters( method, args, query );

        Class<?> returnType = method.getReturnType();

        if (Query.class.isAssignableFrom( returnType ))
            return query;

        Integer result = query.executeUpdate();

        if (Integer.TYPE.isAssignableFrom( returnType ))
            return result;
        return null;
    }

    private Object query(QueryMethod queryMethodAnnotation, Method method, Object[] args)
            throws Exception {

        String namedQueryName = queryMethodAnnotation.value();
        Query query = entityManager.createNamedQuery( namedQueryName );

        setParameters( method, args, query );

        Class<?> returnType = method.getReturnType();

        if (Query.class.isAssignableFrom( returnType ))
            return query;

        if (List.class.isAssignableFrom( returnType )) {
            List<?> resultList = query.getResultList();
            return resultList;
        }

        boolean nullable = queryMethodAnnotation.nullable();
        if (true == nullable) {
            List<?> resultList = query.getResultList();
            if (resultList.isEmpty())
                return null;
            return resultList.get( 0 );
        }

        Object singleResult = query.getSingleResult();
        return singleResult;
    }

    private void setParameters(Method method, Object[] args, Query query) {

        if (null == args)
            return;
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (int paramIdx = 0; paramIdx < args.length; paramIdx++)
            for (Annotation parameterAnnotation : parameterAnnotations[paramIdx])
                if (parameterAnnotation instanceof QueryParam) {
                    QueryParam queryParamAnnotation = (QueryParam) parameterAnnotation;
                    String paramName = queryParamAnnotation.value();
                    query.setParameter( paramName, args[paramIdx] );
                }
    }
}
