export interface PostRequest {
  title: string;
  body: string;
  topic?: string;
}

export interface PostResponse {
  id: number;
  authorId: number;
  authorName: string;
  title: string;
  body: string;
  topic: string | null;
  upvotes: number;
  downvotes: number;
  netVotes: number;
  commentCount: number;
  currentUserVote: 'UPVOTE' | 'DOWNVOTE' | null;
  createdAt: string;
}

export interface CommentResponse {
  id: number;
  postId: number;
  authorId: number;
  authorName: string;
  body: string;
  createdAt: string;
}

export interface PointsProfile {
  userId: number;
  totalPoints: number;
  level: 'NEWCOMER' | 'CONTRIBUTOR' | 'TRUSTED' | 'CHAMPION';
  pointsToNextLevel: number;
  badge: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
