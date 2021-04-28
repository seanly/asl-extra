package cn.k8ops.ant.asl.pipeline.util;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StringMatch {
    @Getter
    List<String> include = new ArrayList<String>();

    @Getter
    List<String> exclude = new ArrayList<String>();

    public StringMatch(List<String> include, List<String> exclude) {
        this.include = include;
        this.exclude = exclude;
    }

    public static StringMatch fromSpecString(Object condition) {
        final List<String> include = new ArrayList<String>();
        final List<String> exclude = new ArrayList<String>();


        if (condition instanceof Map) {
            include.addAll(patterns(((Map) condition).get("include")));
            exclude.addAll(patterns(((Map) condition).get("exclude")));
        } else {
            include.addAll(patterns(condition));
        }

        StringMatch spec = new StringMatch(include, exclude);

        return spec;
    }

    public static List<String> patterns(Object m) {
        if (m instanceof String) {
            return Collections.singletonList((String) m);
        } else if (m instanceof List) {
            return new ArrayList<String>((List<String>) m);
        } else {
            return Collections.emptyList();
        }
    }

    public boolean matches(String condition) {
        if (include.isEmpty() || PatternMatchUtils.simpleMatch(include, condition)) {
            if (exclude.isEmpty() || (!PatternMatchUtils.simpleMatch(exclude, condition))) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return String.format("StringMatch[include=%s, exclude=%s]", include, exclude);
    }
}
