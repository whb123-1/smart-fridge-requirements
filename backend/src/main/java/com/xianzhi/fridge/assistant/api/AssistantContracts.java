package com.xianzhi.fridge.assistant.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssistantContracts {
    private AssistantContracts() { }
    public record CreateConversationRequest(@Size(max=160) String title) { }
    public record ConversationView(UUID id,String title,String status,Instant createdAt) { }
    public record MessageRequest(@NotBlank @Size(max=2000) String content,@Size(max=64) String page,JsonNode selection) { }
    public record MessageView(UUID id,String role,String content,String contextVersion,Instant createdAt) { }
    public record Citation(String type,UUID id,String label) { }
    public record ProposalView(UUID id,String type,String title,String status,String contextVersion,JsonNode payload,Instant expiresAt) { }
    public record MessageResponse(MessageView message,List<Citation> citations,List<ProposalView> actionProposals,boolean fallback) { }
    public record ActionResult(UUID proposalId,String status,JsonNode result) { }
    public record InsightView(UUID id,String type,String title,String body,Instant createdAt) { }
    public record Briefing(List<InsightView> insights,int pendingActionCount,int unreadNotificationCount,boolean fallback) { }
}
