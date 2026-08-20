package com.skala.helpdesk.chat;

import java.util.List;

public record AnswerDto(String answer, List<Source> sources, boolean toolUsed) {

    public record Source(String document, String version) {
    }
}
