package com.example.dawanow.service.ai.chat;

import org.springframework.stereotype.Component;

@Component
public class ChatLanguageDetector {

    /**
     * Detects the retrieval language of a chat message. Any Arabic-script
     * codepoint marks the message as Arabic; everything else defaults to English.
     */
    public String detect(String text) {
        if (text == null || text.isBlank()) {
            return "en";
        }
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character >= '؀' && character <= 'ۿ') {
                return "ar";
            }
        }
        return "en";
    }
}
