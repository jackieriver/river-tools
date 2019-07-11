package com.github.jackieriver.tools.reflect;


import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

@Slf4j
public class AssignObjectFieldValue {
    private final static String PRE_WRAP_CLASS = "java.";

    /**
     * 对外暴露
     *
     * @param clz 指定的实例类型
     * @return 返回一个对内部所有字段赋值的实例
     * @throws Exception
     */
    public static Object newInstanceWithFiedValue(Class clz) throws Exception {
        return newInstanceWithFiedValue(clz.getTypeName());
    }


    /**
     * @param clzTypeName 指定的实例类型的全路径名
     * @return 返回一个对内部所有字段赋值的实例
     * @throws Exception
     */
    private static Object newInstanceWithFiedValue(String clzTypeName) throws Exception {
        String clzName = clzTypeName;
        if (clzTypeName.indexOf("<") != -1) {
            clzName = clzTypeName.substring(0, clzTypeName.indexOf("<"));
        }
        log.info("实例化实例,classType is <{}>", clzTypeName);
        Object filedValue;
        Constructor<?> constructor = null;
        Object[] params = null;
        if (JDK_BAISE_CLASS_TYPES.contains(clzTypeName)) {
            filedValue = JDK_BAISE_CLASS_TYPES_WITH_VALUE.get(clzTypeName);
        } else if (clzTypeName.endsWith("[]")) {
            clzTypeName = clzTypeName.substring(0, clzTypeName.length() - 2);
            Object[] obs = new Object[2];
            Object instance = newInstanceWithFiedValue(clzTypeName);
            obs[0] = obs[1] = instance;
            filedValue = obs;
        } else if (Class.forName(clzName).isAssignableFrom(List.class)) {
            if (List.class.getTypeName().equals(clzName)) {
                List<Object> list = new ArrayList<>();
                if (clzTypeName.indexOf("<") == clzTypeName.lastIndexOf("<")) {
                    String cla = clzTypeName.substring(clzTypeName.indexOf("<") + 1, clzTypeName.indexOf(">"));
                    list.add(newInstanceWithFiedValue(cla));
                    list.add(newInstanceWithFiedValue(cla));
                    list.add(newInstanceWithFiedValue(cla));
                }
                filedValue = list;
            } else {
                filedValue = Collections.EMPTY_LIST;
            }
        } else if (Class.forName(clzTypeName).isAssignableFrom(Map.class)) {
            filedValue = Collections.EMPTY_MAP;
        } else if (clzTypeName.startsWith(PRE_WRAP_CLASS)) {
            filedValue = calculateJdkWarpObjectValue(clzTypeName);
        } else {      //no jdk class type
            filedValue = assignNotJdkObjectFieldValue(clzTypeName);
        }
        log.info("Success to create the bean class type is [{}] with field's value [{}],\n\r it's constructor is [{}] with params [{}]", clzTypeName, filedValue, constructor, params);
        log.info("the bean [{}] parse json is \n\r {} ", clzTypeName, JSON.toJSONString(filedValue));
        return filedValue;
    }


    /**
     * Jdk包装类赋值
     *
     * @param clzTypeName
     * @return
     * @throws Exception
     */
    private static Object calculateJdkWarpObjectValue(String clzTypeName) throws Exception {
        Object filedValue;
        Constructor<?>[] declaredConstructors = Class.forName(clzTypeName).getDeclaredConstructors();
        //now it doesn't suport arrays,so filter the constructor with arrays params
        Optional<Constructor<?>> optional = Arrays.stream(declaredConstructors).filter(c ->
                Arrays.stream(c.getParameterTypes()).allMatch(pt -> !pt.getTypeName().contains("["))
        ).findAny();
        if (!optional.isPresent()) {
            filedValue = null;
        } else {
            Constructor constructor;
            //if the no-args-constructor exist, it will be called first
            Optional<Constructor<?>> noArgsConstructor = Arrays.stream(declaredConstructors).filter(cons -> cons.getParameterCount() == 0).findFirst();
            if (noArgsConstructor.isPresent()) {
                constructor = noArgsConstructor.get();
                constructor.setAccessible(true);
                filedValue = constructor.newInstance();
            } else {
                constructor = optional.get();
                int parameterCount = constructor.getParameterCount();
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                Object[] params = new Object[parameterTypes.length];
                for (int i = 0; i < parameterCount; i++) {
                    params[i] = newInstanceWithFiedValue(parameterTypes[i].getTypeName());
                }
                constructor.setAccessible(true);
                filedValue = constructor.newInstance(params);
            }
        }
        return filedValue;
    }

    /**
     * 非JDK类型对象,即我们定义的Object Class类型所有字段赋值
     *
     * @param clzTypeName
     * @return
     * @throws Exception
     */
    private static Object assignNotJdkObjectFieldValue(String clzTypeName) throws Exception {
        Object filedValue;
        Class<?> clz = Class.forName(clzTypeName);

        Object instance = clz.newInstance();
        Arrays.stream(clz.getDeclaredFields())
                //if the field has default value, skip
                .filter(field -> {
                    field.setAccessible(true);
                    try {
                        return Objects.isNull(field.get(instance));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                    return false;
                }).forEach(field -> {
            try {
                PropertyDescriptor propertyDescriptor = new PropertyDescriptor(field.getName(), clz);
                Method writeMethod = propertyDescriptor.getWriteMethod();
                writeMethod.setAccessible(true);
                Type fieldGenericType = field.getGenericType();
                //TODO 类型不匹配 不支持数组 Object[] 无法转成对应类型的数组
                Object param = newInstanceWithFiedValue(fieldGenericType.getTypeName());
                log.info("set value with params {}, {}, {}", param, field, instance);
                writeMethod.invoke(instance, param);
            } catch (Exception e) {
                log.error("Fail to field[{}] set value, class type is [{}]", field.getName(), field.getType().getTypeName(), e);
            }
        });
        filedValue = instance;
        return filedValue;
    }

    /**
     * java的8个基本类型集合
     * java.lang.String
     * java.util.List
     * java.util.Map
     */
    private final static List<String> JDK_BAISE_CLASS_TYPES = new ArrayList<>(16);

    /**
     * java的8个基本类型+String_默认值
     */
    private final static Map<String, Object> JDK_BAISE_CLASS_TYPES_WITH_VALUE = new HashMap(16);

    static {
        JDK_BAISE_CLASS_TYPES_WITH_VALUE.put(byte.class.getTypeName(), 1);
        JDK_BAISE_CLASS_TYPES_WITH_VALUE.put(boolean.class.getTypeName(), false);
        JDK_BAISE_CLASS_TYPES_WITH_VALUE.put(short.class.getTypeName(), 2);
        JDK_BAISE_CLASS_TYPES_WITH_VALUE.put(char.class.getTypeName(), 43);
        JDK_BAISE_CLASS_TYPES_WITH_VALUE.put(int.class.getTypeName(), 4);
        JDK_BAISE_CLASS_TYPES_WITH_VALUE.put(float.class.getTypeName(), 4.4f);
        JDK_BAISE_CLASS_TYPES_WITH_VALUE.put(double.class.getTypeName(), 8.4d);
        JDK_BAISE_CLASS_TYPES_WITH_VALUE.put(long.class.getTypeName(), 16L);
        JDK_BAISE_CLASS_TYPES_WITH_VALUE.put(String.class.getTypeName(), "65530");

        JDK_BAISE_CLASS_TYPES.addAll(JDK_BAISE_CLASS_TYPES_WITH_VALUE.keySet());
    }
}