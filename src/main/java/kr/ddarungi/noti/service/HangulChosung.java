package kr.ddarungi.noti.service;

public final class HangulChosung {
    private HangulChosung() {}

    private static final char[] CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ".toCharArray();

    public static String extract(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= 0xAC00 && c <= 0xD7A3) sb.append(CHO[(c - 0xAC00) / 588]);
            else sb.append(c);
        }
        return sb.toString();
    }

    public static boolean isAllChosung(String s) {
        if (s.isEmpty()) return false;
        for (char c : s.toCharArray()) {
            if (c < 'ㄱ' || c > 'ㅎ') return false;
        }
        return true;
    }
}
