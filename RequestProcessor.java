package com.omidbiz.action;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.jboss.seam.servlet.ContextualHttpServletRequest;

/**
 * @author Omid Pourhadi : omidpourhadi [AT] gmail [DOT] com 
 * <b>NOTE</b> : use
 *         in Seam ContextualHttpServletRequest
 * @param <E> : entity
 */
public abstract class RequestProcessor<E> extends ContextualHttpServletRequest
{

    protected E instance;
    private Class<E> entityClass;
    HttpServletRequest request;

    public RequestProcessor(HttpServletRequest request)
    {
        super(request);
        this.request = request;
        this.entityClass = getEntityClass();
        if (this.entityClass == null)
            throw new IllegalArgumentException("Generic can not be empty");
        try
        {
            this.instance = this.entityClass.newInstance();
        }
        catch (InstantiationException e)
        {
            e.printStackTrace();
        }
        catch (IllegalAccessException e)
        {
            e.printStackTrace();
        }
        processRequest();
    }

    public Class<E> getEntityClass()
    {
        if (entityClass == null)
        {
            Type type = getClass().getGenericSuperclass();
            if (type instanceof ParameterizedType)
            {
                ParameterizedType paramType = (ParameterizedType) type;
                if (paramType.getActualTypeArguments().length == 2)
                {
                    // likely dealing with -> new
                    // EntityHome<Person>().getEntityClass()
                    if (paramType.getActualTypeArguments()[1] instanceof TypeVariable)
                    {
                        throw new IllegalArgumentException("Could not guess entity class by reflection");
                    }
                    // likely dealing with -> new Home<EntityManager, Person>()
                    // { ... }.getEntityClass()
                    else
                    {
                        entityClass = (Class<E>) paramType.getActualTypeArguments()[1];
                    }
                }
                else
                {
                    // likely dealing with -> new PersonHome().getEntityClass()
                    // where PersonHome extends EntityHome<Person>
                    entityClass = (Class<E>) paramType.getActualTypeArguments()[0];
                }
            }
            else
            {
                throw new IllegalArgumentException("Could not guess entity class by reflection");
            }
        }
        return entityClass;
    }

    public void processRequest()
    {
        try
        {
            List<Field> fields = ReflectionUtil.getFields(entityClass);
            Map<String, String[]> parameterMap = request.getParameterMap();
            Object nestedInstance = null;
            Collection collectionInstance = null;
            for (Map.Entry<String, String[]> param : parameterMap.entrySet())
            {
                String key = param.getKey();
                if (key.indexOf(".") > 0 && key.indexOf("[") < 1)
                {
                    // nested object model.id
                    String nestedFiledName = key.substring(0, key.indexOf(".")); // model
                    String nestedPropertyName = key.substring(key.indexOf(".") + 1); // id
                    for (Field field : fields)
                    {
                        if (field.getName().equals(nestedFiledName))
                        {
                            field.setAccessible(true);
                            Class<?> type = field.getType();
                            if (nestedInstance == null)
                                nestedInstance = type.newInstance();
                            Field nestedField = ReflectionUtil.getField(type, nestedPropertyName);
                            nestedField.setAccessible(true);
                            Class<?> nestedType = nestedField.getType();
                            String val = param.getValue()[0];
                            ReflectionUtil.set(nestedField, nestedInstance, ReflectionUtil.toObject(nestedType, val));
                            //
                            ReflectionUtil.set(field, instance, nestedInstance);
                        }
                    }
                }
                else if (key.indexOf("[") > 0)
                {
                    // handle list roles[0].rolename
                    String nestedFiledName = key.substring(0, key.indexOf("[")); // roles
                    String nestedPropertyName = key.substring(key.indexOf(".") + 1); // rolename
                    int i = 0;
                    for (Field field : fields)
                    {
                        if (field.getName().equals(nestedFiledName))
                        {
                            field.setAccessible(true);
                            Class<?> type = field.getType();
                            if (collectionInstance == null)
                                collectionInstance = ReflectionUtil.instantiateCollection(type);
                            Class<?> genericFieldClassType = ReflectionUtil.getGenericFieldClassType(field);
                            // TODO : if primitive throw exception
                            Object genericInstance = genericFieldClassType.newInstance();
                            Field instanceField = ReflectionUtil.getField(genericFieldClassType, nestedPropertyName);
                            String val = param.getValue()[i];
                            instanceField.setAccessible(true);
                            ReflectionUtil.set(instanceField, genericInstance, ReflectionUtil.toObject(instanceField.getType(), val));
                            //
                            collectionInstance.add(genericInstance);
                            //
                            ReflectionUtil.set(field, instance, collectionInstance);
                            i++;
                        }
                    }
                }
                else
                {
                    for (Field field : fields)
                    {
                        if (field.getName().equals(key))
                        {
                            Class<?> type = field.getType();
                            if (ReflectionUtil.isPrimitive(type) || ReflectionUtil.isWrapper(type))
                            {
                                field.setAccessible(true);
                                ReflectionUtil.set(field, instance, param.getValue()[0]);
                            }

                        }
                    }
                }

            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    public E getInstance()
    {
        return instance;
    }

}
