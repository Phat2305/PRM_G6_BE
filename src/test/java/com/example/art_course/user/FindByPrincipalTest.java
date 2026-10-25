package com.example.art_course.user;

import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// authentication.getName() là UUID (subject JWT): các service phải tra theo id, không theo username.
class FindByPrincipalTest {

    private final UserRepository repo =
            mock(UserRepository.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

    @Test
    void uuidPrincipalLooksUpById() {
        UUID id = UUID.randomUUID();
        User user = new User();
        doReturn(Optional.of(user)).when(repo).findById(id);

        assertSame(user, repo.findByPrincipal(id.toString()).orElseThrow());
        verify(repo, never()).findByUsername(any());
    }

    @Test
    void nonUuidPrincipalFallsBackToUsername() {
        User user = new User();
        doReturn(Optional.of(user)).when(repo).findByUsername("teacher");

        assertSame(user, repo.findByPrincipal("teacher").orElseThrow());
    }

    @Test
    void login_matchesUsernameIgnoringCase_butNotWhenAmbiguous() {
        User user = new User();
        doReturn(List.of(user)).when(repo).findAllByUsernameIgnoreCase("Teacher");
        assertSame(user, repo.findForLogin("Teacher").orElseThrow());

        doReturn(List.of(new User(), new User())).when(repo).findAllByUsernameIgnoreCase("AN");
        assertTrue(repo.findForLogin("AN").isEmpty());

        User exact = new User();
        doReturn(Optional.of(exact)).when(repo).findByUsername("an");
        assertSame(exact, repo.findForLogin("an").orElseThrow());
    }

    @Test
    void unknownOrNullPrincipalIsEmpty() {
        assertTrue(repo.findByPrincipal(UUID.randomUUID().toString()).isEmpty());
        assertTrue(repo.findByPrincipal(null).isEmpty());
    }
}
