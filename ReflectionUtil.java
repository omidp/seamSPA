package com.omidbiz;


import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.JoinColumn;
import javax.persistence.Transient;

import org.apache.commons.beanutils.PropertyUtils;

/**
 * @author Omid Pourhadi : omidpourhadi [AT] gmail [DOT] com
 * 
 */
public class ReflectionUtil
{

    private static final Class<?>[] WRAPPER_TYPES = { int.class, long.class, short.class, float.class, double.class, byte.class,
            boolean.class, char.class };

    public static List<Field> getFields(Class clazz)
    {
        List<Field> fields = new ArrayList<Field>();
        for (Class superClass = clazz; superClass != Object.class; superClass = superClass.getSuperclass())
        {
            for (Field field : superClass.getDeclaredFields())
            {
                fields.add(field);
            }
        }
        return fields;
    }

    public static <E> ArrayList<E> newArrayList(E... elements)
    {
        ArrayList<E> list = new ArrayList<E>(elements.length);
        Collections.addAll(list, elements);
        return list;
    }

    public static Class<?> getGenericFieldClassType(Field field) throws NoSuchFieldException, SecurityException
    {
        field.setAccessible(true);
        ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
        Class<?> genericClass = (Class<?>) parameterizedType.getActualTypeArguments()[0];
        return genericClass;
    }

    public static Field getField(Class clazz, String name)
    {
        for (Class superClass = clazz; superClass != Object.class; superClass = superClass.getSuperclass())
        {
            try
            {
                return superClass.getDeclaredField(name);
            }
            catch (NoSuchFieldException nsfe)
            {
            }
        }
        throw new IllegalArgumentException("no such field: " + clazz.getName() + '.' + name);
    }

    public static Collection<?> instantiateCollection(Class<?> t)
    {

        if (t == Set.class)
        {
            return new HashSet<Object>();
        }
        else if (t == List.class)
        {
            return new ArrayList<Object>();
        }
        else if (t == Map.class)
        {
            throw new RuntimeException("can not instantiate map");
        }
        else if (t == Vector.class)
        {
            throw new RuntimeException("can not instantiate vector");
        }
        else
            throw new RuntimeException("unknown type");
    }

    public static void set(Field field, Object target, Object value) throws Exception
    {
        try
        {
            field.set(target, value);
        }
        catch (IllegalArgumentException iae)
        {
            // target may be null if field is static so use
            // field.getDeclaringClass() instead
            String message = "Could not set field value by reflection: " + field + " on: " + field.getDeclaringClass().getName();
            if (value == null)
            {
                message += " with null value";
            }
            else
            {
                message += " with value: " + value.getClass();
            }
            throw new IllegalArgumentException(message, iae);
        }
    }

    public static boolean isPrimitive(Class type)
    {
        return primitiveTypeFor(type) != null;
    }

    public static boolean isWrapper(Class<?> clazz)
    {
        if (clazz == null)
            throw new RuntimeException("null value");
        for (int i = 0; i < WRAPPER_TYPES.length; i++)
        {
            if (clazz == WRAPPER_TYPES[i])
                return true;
        }
        return false;
    }

    public static boolean isArrayOrCollection(Class<?> clazz)
    {
        if (clazz == null)
            throw new RuntimeException("null value");
        return clazz.isArray() || isSubclass(clazz, Collection.class);
    }

    public static boolean isMap(Class<?> clazz)
    {
        if (clazz == null)
            throw new RuntimeException("null value");
        return isSubclass(clazz, Map.class);
    }

    public static boolean isEnum(Class<?> clazz)
    {
        if (clazz == null)
            throw new RuntimeException("null value");
        return clazz.isEnum();
    }

    public static boolean isSubclass(Class<?> class1, Class<?> class2)
    {
        List<Class<?>> superClasses = getAllSuperclasses(class1);
        List<Class<?>> superInterfaces = getAllInterfaces(class1);
        for (Class<?> c : superClasses)
        {
            if (class2 == c)
                return true;
        }
        for (Class<?> c : superInterfaces)
        {
            if (class2 == c)
                return true;
        }
        return false;
    }

    public static List getAllSuperclasses(Class cls)
    {
        if (cls == null)
        {
            return null;
        }
        List classes = new ArrayList();
        Class superclass = cls.getSuperclass();
        while (superclass != null)
        {
            classes.add(superclass);
            superclass = superclass.getSuperclass();
        }
        return classes;
    }

    public static List getAllInterfaces(Class cls)
    {
        if (cls == null)
        {
            return null;
        }
        List list = new ArrayList();
        while (cls != null)
        {
            Class[] interfaces = cls.getInterfaces();
            for (int i = 0; i < interfaces.length; i++)
            {
                if (list.contains(interfaces[i]) == false)
                {
                    list.add(interfaces[i]);
                }
                List superInterfaces = getAllInterfaces(interfaces[i]);
                for (Iterator it = superInterfaces.iterator(); it.hasNext();)
                {
                    Class intface = (Class) it.next();
                    if (list.contains(intface) == false)
                    {
                        list.add(intface);
                    }
                }
            }
            cls = cls.getSuperclass();
        }
        return list;
    }

    public static Class primitiveTypeFor(Class wrapper)
    {
        if (wrapper == Boolean.class)
            return Boolean.TYPE;
        if (wrapper == Byte.class)
            return Byte.TYPE;
        if (wrapper == Character.class)
            return Character.TYPE;
        if (wrapper == Short.class)
            return Short.TYPE;
        if (wrapper == BigDecimal.class)
            return BigDecimal.class;
        if (wrapper == Date.class)
            return Date.class;
        if (wrapper == java.sql.Date.class)
            return java.sql.Date.class;
        if (wrapper == Integer.class)
            return Integer.TYPE;
        if (wrapper == Long.class)
            return Long.TYPE;
        if (wrapper == Float.class)
            return Float.TYPE;
        if (wrapper == Double.class)
            return Double.TYPE;
        if (wrapper == Void.class)
            return Void.TYPE;
        if (wrapper == String.class)
            return String.class;
        return null;
    }

    public static Object toObject(Class clazz, Object value)
    {
        if (value == null)
            return null;
        if (value instanceof String)
        {
            String v = (String) value;
            if (StringUtil.isEmpty(v))
                return null;
        }
        if (Boolean.class == clazz || boolean.class == clazz)
            return (Boolean) value;
        if (Date.class == clazz)
        {
            // TODO: check for shamsi with anootation on model
            if (value instanceof String)
            {

                String vDate = (String) value;
                String gDate = new PersianCalendarUtil().SolarToGregorian(vDate);
                try
                {
                    return (Date) new SimpleDateFormat("yyyy/MM/dd").parse(gDate);
                }
                catch (ParseException e)
                {
                    e.printStackTrace();
                    return null;
                }
            }
            else
            {
                return (Date) value;
            }
        }
        if (Byte.class == clazz || byte.class == clazz)
            return Byte.parseByte(String.valueOf(value));
        if (Short.class == clazz || short.class == clazz)
            return Short.parseShort(String.valueOf(value));
        if (BigDecimal.class == clazz)
            return new BigDecimal(String.valueOf(value));
        if (Integer.class == clazz || int.class == clazz)
            return Integer.parseInt(String.valueOf(value));
        if (Long.class == clazz || long.class == clazz)
            return Long.parseLong(String.valueOf(value));
        if (Float.class == clazz || float.class == clazz)
            return Float.parseFloat(String.valueOf(value));
        if (Double.class == clazz || double.class == clazz)
            return Double.parseDouble(String.valueOf(value));
        if (String.class == clazz)
            return String.valueOf(value);
        return value;
    }

    public static <E> E entityQueryToSingleObject(List<Map<String, Object>> queryResult, Class<E> clz)
    {
        List<E> list = entityQueryToObject(queryResult, clz);
        if (CollectionUtil.isNotEmpty(list))
        {
            return list.get(0);
        }
        return null;
    }

    public static <E> List<E> entityQueryToObject(List<Map<String, Object>> queryResult, Class<E> clz)
    {
        try
        {
            List<E> instanceList = new ArrayList<E>();
            if (queryResult != null)
            {
                //
                List<Property> properties = getProperties(clz);
                //
                // Map<String, String> maps = Collections.synchronizedMap(new
                // HashMap<String, String>());
                Map<String, String> maps = new HashMap<String, String>();
                if (clz.isAnnotationPresent(AttributeOverrides.class))
                {
                    findRealColumnWithAttributeOverrides(clz, maps);
                }
                if (clz.isAnnotationPresent(AttributeOverride.class))
                {
                    AttributeOverride annotation = clz.getAnnotation(AttributeOverride.class);
                    findRealColumnWithAttributeOverride(annotation, maps);
                }
                //
                for (Map<String, Object> dbRecord : queryResult)
                {

                    E instance = clz.newInstance();
                    for (Property prop : properties)
                    {
                        String columnName = prop.getColumnName();
                        if (maps.isEmpty() == false)
                        {
                            // do we need to check for every iteration ?
                            columnName = maps.get(columnName) == null ? columnName : maps.get(columnName);
                        }
                        Object recordValue = dbRecord.get(columnName);
                        Field field = getField(clz, prop.getFieldName());
                        if (field != null && recordValue != null)
                        {
                            field.setAccessible(true);
                            if (isPrimitive(field.getType()) || isWrapper(field.getType()))
                            {
                                Object val = toObject(field.getType(), recordValue);
                                set(field, instance, val);
                            }
                            else if (field.getType().isEnum())
                            {
                                Enum enVal = Enum.valueOf((Class<Enum>) field.getType(), String.valueOf(recordValue));
                                set(field, instance, enVal);
                            }
                            else
                            {
                                // field is relational entity
                                Object entityInstance = field.getType().newInstance();
                                if (entityInstance instanceof BasePO)
                                {
                                    BasePO po = (BasePO) entityInstance;
                                    po.setId((Long) recordValue);
                                    set(field, instance, entityInstance);
                                }
                            }
                        }
                    }
                    instanceList.add(instance);
                }
                return instanceList;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private static Map<String, String> findRealColumnWithAttributeOverride(AttributeOverride attributeOverride, Map<String, String> maps)
    {

        String atrrName = attributeOverride.name();
        Column column = attributeOverride.column();
        maps.put(atrrName, column.name());
        return maps;
    }

    private static Map<String, String> findRealColumnWithAttributeOverrides(Class<?> clz, Map<String, String> maps)
    {
        if (clz.isAnnotationPresent(AttributeOverrides.class))
        {
            AttributeOverrides annotation = clz.getAnnotation(AttributeOverrides.class);
            AttributeOverride[] value = annotation.value();
            for (int i = 0; i < value.length; i++)
            {
                findRealColumnWithAttributeOverride(value[i], maps);
            }
        }
        return maps;
    }

    /**
     * used for getting Column name and filed name from entity
     * 
     * @param clz
     * @return
     */
    public static List<Property> getProperties(Class<?> clz)
    {
        List<Property> props = new ArrayList<Property>();
        PropertyDescriptor[] descriptors = PropertyUtils.getPropertyDescriptors(clz);
        for (PropertyDescriptor propertyDescriptor : descriptors)
        {
            Method readMethod = propertyDescriptor.getReadMethod();
            if (readMethod != null && readMethod.isAnnotationPresent(Transient.class) == false)
            {
                Column annotation = readMethod.getAnnotation(Column.class);
                if (annotation != null)
                {
                    String dbColumnName = annotation.name();
                    props.add(new Property(dbColumnName, propertyDescriptor.getName()));
                }
                JoinColumn joinAnnotation = readMethod.getAnnotation(JoinColumn.class);
                if (joinAnnotation != null)
                {
                    String dbColumnName = joinAnnotation.name();
                    props.add(new Property(dbColumnName, propertyDescriptor.getName()));
                }
            }
        }
        return props;
    }

    public static <T> T cast(Object value, Class<T> clz)
    {
        if (value == null)
            return null;
        try
        {
            return clz.cast(value);

        }
        catch (ClassCastException e)
        {
            return null;
        }
    }

    public static <T> T cloneBean(Object source, Class<T> target, String... ignoreProperties)
    {
        T clone = null;
        List<String> ignoreList = (ignoreProperties != null) ? Arrays.asList(ignoreProperties) : null;
        try
        {
            clone = target.newInstance();
            if (source == null)
                return clone;
            PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(target);
            if (propertyDescriptors != null)
            {
                for (PropertyDescriptor propertyDescriptor : propertyDescriptors)
                {
                    if (propertyDescriptor.getWriteMethod() != null
                            && (ignoreProperties == null || (!ignoreList.contains(propertyDescriptor.getName()))))
                    {
                        Object propertyValue = PropertyUtils.getProperty(source, propertyDescriptor.getName());
                        PropertyUtils.setProperty(clone, propertyDescriptor.getName(), propertyValue);
                    }
                }
            }
        }
        catch (InstantiationException e)
        {
            e.printStackTrace();
        }
        catch (IllegalAccessException e)
        {
            e.printStackTrace();
        }
        catch (InvocationTargetException e)
        {
            e.printStackTrace();
        }
        catch (NoSuchMethodException e)
        {
            e.printStackTrace();
        }
        return clone;
    }

    public static <T> T cloneBean(Class<T> target, Object source, String... withProperties)
    {
        T clone = null;
        List<String> ignoreList = (withProperties != null) ? Arrays.asList(withProperties) : null;
        try
        {
            clone = target.newInstance();
            if (source == null)
                return clone;
            PropertyDescriptor[] propertyDescriptors = PropertyUtils.getPropertyDescriptors(target);
            if (propertyDescriptors != null)
            {
                for (PropertyDescriptor propertyDescriptor : propertyDescriptors)
                {
                    if (propertyDescriptor.getWriteMethod() != null
                            && (ignoreList == null || (ignoreList.contains(propertyDescriptor.getName()))))
                    {
                        Object propertyValue = PropertyUtils.getProperty(source, propertyDescriptor.getName());
                        PropertyUtils.setProperty(clone, propertyDescriptor.getName(), propertyValue);
                    }
                }
            }
        }
        catch (InstantiationException e)
        {
            e.printStackTrace();
        }
        catch (IllegalAccessException e)
        {
            e.printStackTrace();
        }
        catch (InvocationTargetException e)
        {
            e.printStackTrace();
        }
        catch (NoSuchMethodException e)
        {
            e.printStackTrace();
        }
        return clone;
    }
}
