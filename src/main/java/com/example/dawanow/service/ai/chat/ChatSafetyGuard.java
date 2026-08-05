package com.example.dawanow.service.ai.chat;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Deterministic safety backstop for patient-facing chat.
 *
 * <p>The model classifies intent, but a misclassification must never lead to
 * medicine suggestions for a potentially life-threatening complaint. This guard
 * scans the patient's own words for high-severity red flags and, when one is
 * present, forces the conversation to a "see a doctor" response with no product
 * suggestions. Terms are deliberately high-precision multi-word phrases so that
 * everyday idioms (for example "من قلبي") do not trigger it.</p>
 */
@Component
public class ChatSafetyGuard {

    private static final List<String> RED_FLAGS = List.of(
            // Cardiac and circulatory
            "chest pain", "pain in my chest", "chest tightness", "tightness in my chest",
            "heart attack", "cardiac arrest", "palpitations", "stroke",
            "الم في الصدر", "الم بالصدر", "وجع في الصدر", "وجع بالصدر", "ضغط علي صدري",
            "الم في صدري", "وجع في صدري", "ذبحه صدريه", "جلطه", "سكته دماغيه", "ازمه قلبيه",
            // Breathing
            "can not breathe", "cant breathe", "can't breathe", "difficulty breathing",
            "trouble breathing", "shortness of breath", "struggling to breathe",
            "مش قادر اتنفس", "لا استطيع التنفس", "صعوبه في التنفس", "ضيق في التنفس",
            "ضيق تنفس", "كتمه في النفس",
            // Bleeding and trauma
            "severe bleeding", "heavy bleeding", "bleeding heavily", "vomiting blood",
            "coughing blood", "blood in stool", "blood in urine", "head injury",
            "نزيف شديد", "نزيف غزير", "ترجيع دم", "قيء دم", "دم في البراز", "دم في البول",
            "اصابه في الراس", "ضربه في الراس",
            // Neurological and consciousness
            "unconscious", "passed out", "fainted", "seizure", "convulsion",
            "slurred speech", "sudden numbness", "sudden weakness", "loss of vision",
            "worst headache of my life", "stiff neck",
            "فقد الوعي", "فاقد الوعي", "اغماء", "غمي عليه", "تشنجات", "تشنج",
            "شلل", "تنميل مفاجئ", "ضعف مفاجئ", "فقدان البصر", "تيبس الرقبه",
            // Poisoning and self-harm
            "poisoning", "overdose", "took too many pills", "suicide", "kill myself",
            "تسمم", "جرعه زائده", "انتحار", "اذيه نفسي",
            // Severe abdominal
            "severe abdominal pain", "severe stomach pain",
            "الم شديد في البطن", "وجع شديد في البطن", "مغص شديد"
    );

    /**
     * @return true when the patient's message contains a high-severity red flag
     *         and the assistant must not suggest any medicine.
     */
    public boolean hasRedFlag(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = normalize(message);
        return RED_FLAGS.stream().anyMatch(flag -> normalized.contains(normalize(flag)));
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا')
                .replace('ى', 'ي')
                .replace('ؤ', 'و')
                .replace('ئ', 'ي')
                .replace('ة', 'ه')
                .replace("ـ", "")
                .replaceAll("[\\u064B-\\u065F\\u0670]", "")
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
