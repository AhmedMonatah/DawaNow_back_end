package com.example.dawanow.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dawanow.entity.ChatConversation;
import com.example.dawanow.entity.ChatMessage;
import com.example.dawanow.entity.ChatMessageRole;
import com.example.dawanow.entity.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
class ChatConversationRepositoryTest {

    @Autowired
    private ChatConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void dropBrokenUserCheckConstraints() {
        // H2 generates an unevaluable check constraint for the single-table `user`
        // discriminator column; drop it so this test can insert users directly.
        List<String> constraints = jdbcTemplate.queryForList(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_name = 'user' AND constraint_type = 'CHECK'",
                String.class
        );
        for (String constraint : constraints) {
            jdbcTemplate.execute("ALTER TABLE \"user\" DROP CONSTRAINT IF EXISTS \"" + constraint + "\"");
        }
    }

    @Test
    void findsTheUsersOwnConversationOnly() {
        User owner = saveUser("owner@example.com", "+201000000001");
        User other = saveUser("other@example.com", "+201000000002");
        ChatConversation conversation = saveConversation(owner);

        assertThat(conversationRepository.findFirstByUserIdOrderByIdAsc(owner.getId()))
                .get()
                .extracting(ChatConversation::getId)
                .isEqualTo(conversation.getId());
        assertThat(conversationRepository.findFirstByUserIdOrderByIdAsc(other.getId())).isEmpty();
    }

    @Test
    void returnsTheOldestConversationWhenSeveralExist() {
        User owner = saveUser("owner2@example.com", "+201000000003");
        ChatConversation first = saveConversation(owner);
        saveConversation(owner);

        assertThat(conversationRepository.findFirstByUserIdOrderByIdAsc(owner.getId()))
                .get()
                .extracting(ChatConversation::getId)
                .isEqualTo(first.getId());
    }

    @Test
    void deleteByConversationIdRemovesOnlyThatConversationsMessages() {
        User owner = saveUser("owner3@example.com", "+201000000005");
        ChatConversation first = saveConversation(owner);
        ChatConversation second = saveConversation(owner);
        saveMessage(first, "hello");
        saveMessage(first, "hi there");
        saveMessage(second, "unrelated");

        messageRepository.deleteByConversationId(first.getId());

        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(first.getId())).isEmpty();
        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(second.getId())).hasSize(1);
    }

    private User saveUser(String email, String phoneNumber) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setPhoneNumber(phoneNumber);
        user.setPassword("secret");
        return userRepository.save(user);
    }

    private ChatConversation saveConversation(User user) {
        ChatConversation conversation = new ChatConversation();
        conversation.setUser(user);
        conversation.setTitle("Test conversation");
        return conversationRepository.save(conversation);
    }

    private void saveMessage(ChatConversation conversation, String content) {
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setRole(ChatMessageRole.USER);
        message.setContent(content);
        messageRepository.save(message);
    }
}
