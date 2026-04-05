export interface TicketRequest {
  orderId: number;
  type: TicketType;
  description: string;
  refundAmount?: number;
}

export interface TicketResponse {
  id: number;
  orderId: number;
  customerId: number;
  customerName: string;
  assignedToId: number | null;
  type: TicketType;
  status: TicketStatus;
  description: string;
  refundAmount: number | null;
  autoApproved: boolean;
  slaBreached: boolean;
  firstResponseDueAt: string | null;
  createdAt: string;
  updatedAt: string;
  evidence: EvidenceDto[];
}

export interface EvidenceDto {
  id: number;
  fileName: string;
  contentType: string;
  fileSize: number;
  sha256Hash: string;
  createdAt: string;
}

export type TicketType = 'REFUND_ONLY' | 'RETURN_AND_REFUND';

export type TicketStatus =
  | 'OPEN' | 'AWAITING_EVIDENCE' | 'UNDER_REVIEW' | 'APPROVED'
  | 'REJECTED' | 'REFUNDED' | 'RETURN_SHIPPED' | 'RETURN_RECEIVED'
  | 'CLOSED' | 'ESCALATED';
