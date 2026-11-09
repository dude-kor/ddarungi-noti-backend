package kr.ddarungi.noti.domain.station;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits the Open API {@code stationName} into station number, display name and status tags.
 *
 * <pre>
 *   "102. 망원역 1번출구 앞"        -> no=102   name=망원역 1번출구 앞
 *   "[점검중] 102. 망원역"          -> no=102   name=망원역          tags=[점검중]  outOfService
 *   "(임시) 3001. 임시 거치소"      -> no=3001  name=임시 거치소     tags=(임시)   temporary
 *   "서울숲역 4번출구"              -> no=null  name=서울숲역 4번출구
 * </pre>
 *
 * FIX: the old code used one greedy regex that required {@code ^\d+\.}. A leading tag, a 20-digit number or an
 * empty remainder produced wrong data (or, in the split()-based variants this replaces, ArrayIndexOutOfBounds /
 * NumberFormatException). This parser never converts the number to a numeric type, never indexes into a split array,
 * bounds every quantifier (no catastrophic backtracking) and always returns a non-null result.
 */
public final class StationNameParser {

    private static final int MAX_INPUT = 512;
    private static final int MAX_TAGS = 3;

    /** "[점검중] ", "(임시) ", full-width brackets too. Content: 1-30 chars, no nested brackets. */
    private static final Pattern LEADING_TAG =
            Pattern.compile("^[\\[(\\uFF3B\\uFF08]\\s*([^\\[\\]()\\uFF3B\\uFF3D\\uFF08\\uFF09]{1,30}?)\\s*[\\])\\uFF3D\\uFF09]\\s*");

    /** "102." / "102 ." / "102．" - up to 9 digits; "1.5km" is NOT a number prefix (digit after the dot). */
    private static final Pattern LEADING_NUMBER =
            Pattern.compile("^([0-9]{1,9})\\s*[.\\uFF0E\\u3002](?![0-9])\\s*");

    private static final Pattern OUT_OF_SERVICE =
            Pattern.compile("점검|폐쇄|중지|휴업|철거|이전|종료|불가|운영\\s*안");
    private static final Pattern TEMPORARY = Pattern.compile("임시");

    private StationNameParser() {
    }

    /**
     * @param number       digits of the station number, or {@code null} if the name had none
     * @param name         display name; empty (never null) when nothing meaningful was left
     * @param tags         bracketed tags in order of appearance, without brackets
     * @param outOfService a tag says the station is under maintenance / closed
     * @param temporary    a tag says the station is temporary
     */
    public record Parsed(String number, String name, List<String> tags, boolean outOfService, boolean temporary) {
    }

    public static Parsed parse(String raw) {
        String rest = normalize(raw);
        List<String> tags = new ArrayList<>();

        rest = stripTags(rest, tags);

        String number = null;
        Matcher num = LEADING_NUMBER.matcher(rest);
        if (num.find()) {
            number = num.group(1);
            rest = rest.substring(num.end());
            rest = stripTags(rest, tags);        // "102. [점검중] 망원역"
        }

        String joined = String.join(" ", tags);
        return new Parsed(number, rest.strip(), List.copyOf(tags),
                OUT_OF_SERVICE.matcher(joined).find(), TEMPORARY.matcher(joined).find());
    }

    private static String stripTags(String s, List<String> tags) {
        String rest = s;
        for (int i = 0; i < MAX_TAGS; i++) {
            Matcher m = LEADING_TAG.matcher(rest);
            if (!m.find()) break;
            tags.add(m.group(1).strip());
            rest = rest.substring(m.end());
        }
        return rest;
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.length() > MAX_INPUT ? raw.substring(0, MAX_INPUT) : raw;
        return s.replace(' ', ' ').replace('　', ' ').replace('﻿', ' ').strip();
    }
}
