package com.rtsbuilding.rtsbuilding.server.task.identity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskIdentityTest {

    @Test
    void legacySubmissionIsStableAndNamespaced() {
        UUID owner = UUID.fromString("45b15819-45a2-482f-898e-baf86c6e9efa");

        SubmissionId first = SubmissionId.fromLegacy(owner, "placement", "workflow:17");
        SubmissionId retry = SubmissionId.fromLegacy(owner, "placement", "workflow:17");

        assertEquals(first, retry);
        assertNotEquals(first, SubmissionId.fromLegacy(owner, "mining", "workflow:17"));
        assertNotEquals(first, SubmissionId.fromLegacy(UUID.randomUUID(), "placement", "workflow:17"));
    }

    @Test
    void taskIdDerivedFromSubmissionIsStablePerOwner() {
        UUID owner = UUID.fromString("f1393d72-b3bb-4df8-880d-1ba82d412b74");
        SubmissionId submission = new SubmissionId(
                UUID.fromString("f65e8d5a-119c-45b6-8f79-2786da9e1a88"));

        TaskId first = TaskId.fromSubmission(owner, submission);

        assertEquals(first, TaskId.fromSubmission(owner, submission));
        assertNotEquals(first, TaskId.fromSubmission(UUID.randomUUID(), submission));
        assertEquals(first, TaskId.parse(first.toString()));
        assertEquals(submission, SubmissionId.parse(submission.toString()));
    }

    @Test
    void legacyIdentityRejectsAmbiguousEmptyParts() {
        UUID owner = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> SubmissionId.fromLegacy(owner, " ", "workflow:1"));
        assertThrows(IllegalArgumentException.class,
                () -> SubmissionId.fromLegacy(owner, "placement", ""));
    }
}
