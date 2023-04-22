package utils.communication.serializer.utils;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

public class FieldRegister
{
    public static Set<Class<?>> visitFields(Class<?> targetObj, Set<Class<?>> visitedFields)
    {
        for(var field : targetObj.getFields())
        {
            Class<?> fieldType = field.getType();

            if(/*!fieldType.isPrimitive() &&*/!visitedFields.contains(fieldType))
            {
                visitedFields.add(fieldType);

                visitedFields = visitFields(fieldType, visitedFields);
            }
        }

        return visitedFields;
    }

    /**
     * Return a set of all visible class fields' types
     * @param targetObj Class to observe
     * @return Set with visible instance/class variable types
     */
    public static Set<Class<?>> visitFields(Class<?> targetObj)
    {
        Set<Class<?>> visibleFields = new HashSet<>();

        for(Field field : targetObj.getFields())
        {
            Class<?> fieldType = field.getType();

            if (/*!fieldType.isPrimitive() && */!visibleFields.contains(fieldType))
            {
                visibleFields.add(fieldType);

                visibleFields = visitFields(fieldType, visibleFields);
            }
        }

        return visibleFields;
    }
}
