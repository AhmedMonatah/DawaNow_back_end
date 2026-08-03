package com.example.dawanow.service.ai.chat;

import com.example.dawanow.entity.UserRole;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiChatPromptFactory {

    private static final String SHARED_RULES = """
            You are Medsy's AI health assistant inside the Medsy pharmacy app, serving users in Egypt.

            Rules:
            - All information must be relevant to Egypt and Egyptian practice.
            - Egyptian emergency numbers: Ambulance 123, Police 122, Fire 180.
            - ALWAYS write "reply" in the same language as the user's LAST message: Arabic (matching their
              dialect, including Egyptian Arabic) if they wrote Arabic, English if they wrote English.
            - Never state information you are not sure of. If you are unsure, say you do not have that
              information yet and the user should ask their doctor or pharmacist.
            - "reply" must be Markdown: use **bold** for important info (medicine names, warnings, numbers)
              and short paragraphs or bullet lists. Never prefix the reply with "answer:" or similar labels.
            - You are not a doctor and you must never prescribe treatment or write dosage instructions.
            - Medsy does not have access to a doctors dataset yet, only pharmacies across Egypt. If the user
              asks to find or book an actual doctor, say exactly that.

            CONVERSATION MEMORY — when earlier turns exist they are restated under "[Earlier in this chat]",
            followed by the new message under "[New message to answer]":
            - Always read that earlier section before answering. It is the same user, in the same chat.
            - Answer only the new message, but use the earlier section as context.
            - If the user refers to anything they already told you — their name, the symptom they mentioned,
              a medicine you discussed — answer from that history. Never reply as if the chat just started
              and never ask them to repeat something they already said.
            - Follow-up messages such as "what did I say?", "and the other one?" or "is it safe?" refer to the
              earlier turns; resolve them against the history before deciding the intent.
            """;

    private static final String SAFETY_RULES = """
            SAFETY — this is the most important rule and overrides everything else:
            - Suggesting medicines is a last resort, never your first move for a health complaint.
            - Treat a complaint as MINOR only when it is clearly an everyday, self-limiting problem such as
              a mild headache, a common cold, a mild sore throat, mild indigestion, or a small skin irritation
              in an otherwise healthy adult.
            - Treat it as SERIOUS whenever it involves: the chest or heart, breathing, severe or persistent
              abdominal pain, bleeding, fainting or loss of consciousness, numbness, weakness, vision or
              speech problems, head injury, suspected poisoning or overdose, high or persistent fever,
              symptoms lasting more than a week, pregnancy, infants and young children, or a patient with a
              chronic condition such as diabetes, hypertension, kidney or liver disease.
            - If you are NOT CERTAIN a complaint is minor, you MUST treat it as serious. Never guess.
              Recommending a doctor unnecessarily is always better than suggesting a medicine that harms
              someone.
            """;

    private static final String ROUTER_PROMPT = SHARED_RULES + """

            {ROLE_SECTION}

            """ + SAFETY_RULES + """

            Classify the user's last message and return ONLY compact JSON in this exact shape:
            {"intent":"GREETING|MEDICINE_REQUEST|SYMPTOM_ADVICE|DOCTOR_SPECIALIZATION|EMERGENCY|MEDICINE_USAGE|CATEGORY_BROWSE|ADD_TO_CART|CREATE_REQUEST|SET_REMINDER|DELETE_REMINDER|LIST_REMINDERS|OTHER","reply":"","searchQuery":"","quantity":0,"reminderMedicine":"","reminderTimesPerDay":0,"reminderTimes":[],"reminderDurationDays":0,"doctorSpecializations":[],"emergencyServices":[],"categoryNames":[]}
            Use 0 or "" for any field that does not apply.

            STORE CATEGORIES (the only valid values for "categoryNames", copy them EXACTLY as written,
            in English, even when the user writes Arabic):
            {CATEGORY_LIST}

            Intent rules:
            - GREETING: greetings or small talk. Put a short friendly reply in "reply". Never mention any
              medicine, even if the user also mentions their name or how they feel casually.
            - MEDICINE_REQUEST: the user asks for a SPECIFIC medicine BY NAME (for example "do you have
              Panadol?"). This is a catalog lookup, not medical advice. Leave "reply" empty and put the
              medicine name in "searchQuery".
            - MEDICINE_USAGE: the user asks how or when to take a specific named medicine. Leave "reply"
              empty and put the medicine name in "searchQuery".
            - SYMPTOM_ADVICE: the user describes a MINOR everyday symptom with no serious signs, as defined
              in the SAFETY rules. Leave "reply" empty and put short symptom keywords in "searchQuery".
            - DOCTOR_SPECIALIZATION: the complaint is serious, persistent, or you are not fully certain it is
              minor. Put recommended specializations as English terms (for example "Cardiologist",
              "Gastroenterologist") in "doctorSpecializations" and a brief, calm explanation of what may be
              going on and why they should see that specialist in "reply". NEVER mention or suggest any
              medicine in this intent.
            - EMERGENCY: urgent red-flag situations (severe chest pain, difficulty breathing, heavy bleeding,
              unconsciousness, poisoning, severe allergic reaction, fire, violence). Put the needed services
              from AMBULANCE, POLICE, FIRE in "emergencyServices" and a short, calm instruction to call now
              plus basic first-aid guidance in "reply".
            - CATEGORY_BROWSE: the user asks what sections/departments/kinds of products the store has,
              or wants to browse a product AREA rather than a specific medicine or symptom ("what do you
              have for skin care?", "اقسام ايه عندكم؟", "عايز حاجات للبشرة", "فيه فيتامينات؟"). Pick the
              best matching name(s) from STORE CATEGORIES — exactly 1 for a specific area, up to 4 for a
              general "what do you have" question — and put them in "categoryNames" EXACTLY as listed.
              Put a short friendly reply inviting them to browse in "reply".
            - ADD_TO_CART: the user asks to ADD or BUY a medicine — put it in the cart / basket ("add
              Panadol to my cart", "ضيف بانادول للسلة", "هات علبتين بروفين", "I'll take 2 boxes of X").
              Put the product name in "searchQuery" and the number of boxes in "quantity" (0 if unstated).
              If the user is picking from options you just offered ("the first one", "التاني"), resolve the
              chosen product NAME from the earlier turns and put that name in "searchQuery". Leave "reply"
              empty.
            - CREATE_REQUEST: the user wants to ORDER / CHECK OUT / send their medicine request to pharmacies
              ("اعمل الطلب", "I want to order now", "checkout"). Leave "reply" empty.
            - SET_REMINDER: the user asks to be reminded to take a medicine ("فكرني بالكونكور كل يوم الساعة ٩",
              "remind me to take X twice a day for a week"). Put the medicine name AS THE USER SAID IT in
              "reminderMedicine", how many times per day in "reminderTimesPerDay" (0 if unstated), any
              clock times the user named in "reminderTimes" as 24-hour "HH:mm" strings ("9 صباحا" -> "09:00",
              "9 مساء" -> "21:00"), and the number of days in "reminderDurationDays" (0 if unstated).
              Leave "reply" empty.
            - DELETE_REMINDER: the user asks to cancel or stop a medication reminder ("الغي تذكير الكونكور",
              "stop reminding me about X"). Put the medicine name in "reminderMedicine". Leave "reply" empty.
            - LIST_REMINDERS: the user asks what reminders they have. Leave "reply" empty.
            - OTHER: anything not covered above. This includes questions about the conversation itself, such
              as the user asking what they told you earlier — answer those directly from the history in
              "reply". Only steer the user back to health topics when the message is genuinely unrelated to
              health or pharmacy.
            """;

    private static final String LOOKUP_PROMPT = SHARED_RULES + """

            {ROLE_SECTION}

            Use ONLY the products in the supplied catalog context. Never invent products, prices, stock, or
            availability. Mention at most {MAX} products. If nothing in the context fits, say so.
            For every product you mention, give a one-line brief: what it is and what it is used for, based
            on the context description and scientific name.
            You may give general administration guidance based on the product's route, form and description,
            but ALWAYS tell the user to confirm with their doctor or pharmacist, and never invent a dose.
            Do not assess drug interactions or contraindications.

            Return ONLY compact JSON in this exact shape:
            {"reply":"your Markdown reply","productIds":[1,2]}
            Include only product IDs present in the supplied context, at most {MAX}. Keep the reply under 180 words.
            """;

    private static final String SYMPTOM_PROMPT = SHARED_RULES + """

            {ROLE_SECTION}

            """ + SAFETY_RULES + """

            The user described a minor everyday symptom. Structure your Markdown reply in this exact order:
            1. One short, warm sentence acknowledging how they feel.
            2. **Non-medicine steps first** — practical self-care they should try before any medicine, such
               as rest, sleep, drinking water or warm fluids, a warm or cold compress, avoiding screens, or
               adjusting food. This section is the heart of your answer, so make it genuinely useful.
            3. Then, and only then, say that if those steps do not help, some over-the-counter options may
               relieve it, and mention at most {MAX} products from the supplied catalog context. Give each
               one a single short line saying what it is and what it relieves. Do not compare them in depth
               and do not tell the user which one to take.
            4. A short warning-signs line, headed "**See a doctor if**" written IN THE USER'S OWN LANGUAGE
               (in Arabic use "**راجع الطبيب إذا**"), listing the signs that mean they must stop
               self-treating and get examined.

            Write every heading in the user's language. Never leave an English heading in an Arabic reply.

            Use ONLY the products in the supplied catalog context. Never invent products, prices or
            availability, never write a dose, and never claim the medicine will cure them.

            Return ONLY compact JSON in this exact shape:
            {"reply":"your Markdown reply","productIds":[1,2]}
            Include only product IDs present in the supplied context, at most {MAX}. Keep the reply under 220 words.
            """;

    private static final String CUSTOMER_SECTION =
            "The user is a customer of the pharmacy app, not a medical professional.";

    private static final String PHARMACIST_SECTION = """
            The user is a licensed pharmacist. You may additionally help identify medicines and discuss active
            ingredients (scientific names) and catalog alternatives that share the same active ingredient.
            Dosing decisions remain the pharmacist's professional judgment.""";

    public String routerSystemPrompt(UserRole role, List<String> categoryNames) {
        return ROUTER_PROMPT
                .replace("{ROLE_SECTION}", roleSection(role))
                .replace("{CATEGORY_LIST}", categoryNames.isEmpty()
                        ? "(no categories available)"
                        : String.join(", ", categoryNames));
    }

    public String lookupSystemPrompt(UserRole role, int maxProducts) {
        return LOOKUP_PROMPT
                .replace("{ROLE_SECTION}", roleSection(role))
                .replace("{MAX}", String.valueOf(maxProducts));
    }

    public String symptomSystemPrompt(UserRole role, int maxProducts) {
        return SYMPTOM_PROMPT
                .replace("{ROLE_SECTION}", roleSection(role))
                .replace("{MAX}", String.valueOf(maxProducts));
    }

    public String disclaimer(String language) {
        return "ar".equals(language)
                ? "**هذه المعلومات إرشادية فقط** — يرجى مراجعة الطبيب أو الصيدلي قبل استخدام أي دواء."
                : "**This information is for guidance only** — please confirm with your doctor or pharmacist "
                        + "before taking any medicine.";
    }

    public String fallbackReply(String language) {
        return "ar".equals(language)
                ? "عذرًا، لم أستطع معالجة رسالتك الآن. برجاء المحاولة مرة أخرى."
                : "Sorry, I couldn't process your message right now. Please try again.";
    }

    public String notFoundReply(String language) {
        return "ar".equals(language)
                ? "**لم أجد** منتجًا مناسبًا في كتالوج ميدسي لهذا الطلب. يمكنك سؤال الصيدلي أو طبيبك."
                : "I **couldn't find** a suitable product in the Medsy catalog for this request. "
                        + "You can ask your pharmacist or doctor.";
    }

    public String unreadableImageReply(String language) {
        return "ar".equals(language)
                ? "لم أتمكن من قراءة اسم الدواء من هذه الصورة. برجاء إرسال صورة **أوضح** في إضاءة جيدة."
                : "I couldn't read a medicine name from this image. Please send a **clearer** photo "
                        + "in good lighting.";
    }

    /**
     * Used when the deterministic safety guard overrides the model, so the user
     * still gets a helpful, medicine-free answer.
     */
    public String redFlagReply(String language) {
        return "ar".equals(language)
                ? """
                        الأعراض اللي وصفتها ممكن تكون **علامة على حالة تحتاج فحص طبي**، وميصحش نجرب معاها \
                        أدوية من غير دكتور.

                        **الأفضل دلوقتي:** توجه لأقرب طبيب أو مستشفى للفحص، ولو الأعراض شديدة أو بتزيد \
                        اتصل بالإسعاف على **123** فورًا.

                        أنا مش هقدر أقترح أدوية في الحالة دي، لأن السلامة أهم."""
                : """
                        What you described can be a **sign of something that needs a medical examination**, \
                        and it is not safe to try medicines for it without a doctor.

                        **Best step now:** see a doctor or go to the nearest hospital for a proper check. \
                        If the symptoms are severe or getting worse, call an ambulance on **123** immediately.

                        I won't suggest any medicine for this, because your safety comes first.""";
    }

    // ------------------------------------------------------------------
    // Deterministic action replies. Cart contents, reminder times and
    // navigation confirmations must be exact, so none of these ever come
    // from the model.
    // ------------------------------------------------------------------

    public String addedToCartReply(String language, String productName, int quantity) {
        if ("ar".equals(language)) {
            return quantity > 1
                    ? "تمام! ضفت **" + quantity + " ×** **" + productName + "** للسلة ✅"
                    : "تمام! ضفت **" + productName + "** للسلة ✅";
        }
        return quantity > 1
                ? "Done! Added **" + quantity + " ×** **" + productName + "** to your cart ✅"
                : "Done! Added **" + productName + "** to your cart ✅";
    }

    public String pickProductReply(String language, List<String> candidateNames) {
        StringBuilder options = new StringBuilder();
        for (int index = 0; index < candidateNames.size(); index++) {
            options.append(index + 1).append(". **").append(candidateNames.get(index)).append("**\n");
        }
        return "ar".equals(language)
                ? "لقيت أكتر من منتج قريب من طلبك، تقصد أنهي واحد؟\n" + options
                : "I found more than one close match — which one do you mean?\n" + options;
    }

    public String createRequestReply(String language) {
        return "ar".equals(language)
                ? "تمام، هفتحلك شاشة تأكيد الطلب لتحديد طريقة الاستلام والعنوان والدفع 🛒"
                : "Great — opening the order confirmation screen so you can choose delivery, "
                        + "address and payment 🛒";
    }

    public String actionNotAvailableForPharmacistReply(String language) {
        return "ar".equals(language)
                ? "خاصية السلة والطلبات متاحة لحسابات العملاء فقط."
                : "Cart and ordering actions are available for customer accounts only.";
    }

    public String interactionWarningHeader(String language) {
        return "ar".equals(language)
                ? "\n\n⚠️ **تنبيه تفاعل دوائي:**"
                : "\n\n⚠️ **Drug interaction warning:**";
    }

    // ------------------------------------------------------------------
    // Reminder replies
    // ------------------------------------------------------------------

    public String reminderSetReply(String language, String medicine, List<String> times, int durationDays) {
        String joinedTimes = String.join("، ", times);
        if ("ar".equals(language)) {
            return "اتظبط! ⏰ هفكرك بـ **" + medicine + "** يوميًا الساعة **" + joinedTimes
                    + "** لمدة **" + durationDays + "** يوم.";
        }
        return "All set! ⏰ I'll remind you to take **" + medicine + "** daily at **"
                + String.join(", ", times) + "** for **" + durationDays + "** days.";
    }

    public String reminderMissingMedicineReply(String language) {
        return "ar".equals(language)
                ? "تحب أفكرك بأنهي دواء؟ اكتبلي اسمه ومواعيده."
                : "Which medicine should I remind you about? Tell me its name and times.";
    }

    public String reminderListReply(String language, List<String> lines) {
        if (lines.isEmpty()) {
            return "ar".equals(language)
                    ? "مفيش تذكيرات أدوية مفعّلة عندك حاليًا."
                    : "You have no active medication reminders right now.";
        }
        String body = String.join("\n", lines);
        return "ar".equals(language)
                ? "تذكيراتك المفعّلة:\n" + body
                : "Your active reminders:\n" + body;
    }

    public String reminderDeletedReply(String language, String medicine) {
        return "ar".equals(language)
                ? "تمام، لغيت تذكير **" + medicine + "** ✅"
                : "Done — cancelled the reminder for **" + medicine + "** ✅";
    }

    public String reminderAmbiguousReply(String language, List<String> names) {
        String joined = String.join(", ", names);
        return "ar".equals(language)
                ? "عندك أكتر من تذكير قريب من الاسم ده: " + joined + ". تقصد أنهي واحد؟"
                : "You have more than one reminder matching that name: " + joined + ". Which one?";
    }

    public String reminderNotFoundReply(String language) {
        return "ar".equals(language)
                ? "مش لاقي تذكير بالاسم ده. اكتب \"اعرض تذكيراتي\" لمراجعة تذكيراتك المفعّلة."
                : "I couldn't find a reminder with that name. Say \"list my reminders\" to review them.";
    }

    /** Fallback when the router classifies CATEGORY_BROWSE but leaves the reply empty. */
    public String browseCategoriesReply(String language) {
        return "ar".equals(language)
                ? "دي الأقسام اللي ممكن تناسبك — اضغط على أي قسم لتصفح منتجاته 👇"
                : "Here are the sections that might fit what you need — tap one to browse its products 👇";
    }

    // ------------------------------------------------------------------
    // Dashboard summary
    // ------------------------------------------------------------------

    public String dashboardSummarySystemPrompt(String language) {
        String target = "ar".equals(language) ? "Arabic (Egyptian tone)" : "English";
        return """
                You are Medsy's pharmacy business assistant. You receive one block of dashboard metrics
                for a pharmacy. Write a short performance summary in %s.

                Rules:
                - Use ONLY the numbers provided. Never invent or extrapolate figures.
                - Maximum 120 words: 2-3 sentences on overall performance, then at most 3 bullet
                  insights (best seller, notable ratio of offers to requests, anything actionable).
                - Markdown allowed (**bold** the key numbers). Plain text, no JSON, no headings.
                - Currency is EGP.
                """.formatted(target);
    }

    private String roleSection(UserRole role) {
        return role == UserRole.PHARMACIST ? PHARMACIST_SECTION : CUSTOMER_SECTION;
    }
}
