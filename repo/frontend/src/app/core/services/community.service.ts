import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PostRequest, PostResponse, CommentResponse, PointsProfile, PageResponse } from '../models/community.model';

@Injectable({ providedIn: 'root' })
export class CommunityApiService {
  private readonly API = '/api/community';

  constructor(private http: HttpClient) {}

  // Posts
  createPost(request: PostRequest): Observable<PostResponse> {
    return this.http.post<PostResponse>(`${this.API}/posts`, request);
  }

  getFeed(page = 0, size = 20): Observable<PageResponse<PostResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<PostResponse>>(`${this.API}/posts`, { params });
  }

  getByTopic(topic: string, page = 0, size = 20): Observable<PageResponse<PostResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<PostResponse>>(`${this.API}/posts/topic/${topic}`, { params });
  }

  getFollowedFeed(): Observable<PostResponse[]> {
    return this.http.get<PostResponse[]>(`${this.API}/posts/following`);
  }

  removePost(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/posts/${id}`);
  }

  // Comments
  addComment(postId: number, body: string): Observable<CommentResponse> {
    return this.http.post<CommentResponse>(`${this.API}/posts/${postId}/comments`, { body });
  }

  getComments(postId: number): Observable<CommentResponse[]> {
    return this.http.get<CommentResponse[]>(`${this.API}/posts/${postId}/comments`);
  }

  // Votes
  vote(postId: number, type: 'UPVOTE' | 'DOWNVOTE'): Observable<PostResponse> {
    return this.http.post<PostResponse>(`${this.API}/posts/${postId}/vote`, null, { params: { type } });
  }

  // Topics
  followTopic(topic: string): Observable<void> {
    return this.http.post<void>(`${this.API}/topics/${topic}/follow`, null);
  }

  unfollowTopic(topic: string): Observable<void> {
    return this.http.delete<void>(`${this.API}/topics/${topic}/follow`);
  }

  getFollowedTopics(): Observable<string[]> {
    return this.http.get<string[]>(`${this.API}/topics/following`);
  }

  // Points
  getMyPoints(): Observable<PointsProfile> {
    return this.http.get<PointsProfile>(`${this.API}/points/me`);
  }

  getUserPoints(userId: number): Observable<PointsProfile> {
    return this.http.get<PointsProfile>(`${this.API}/points/${userId}`);
  }

  // Favorites
  getMyFavorites(): Observable<number[]> {
    return this.http.get<number[]>(`${this.API}/favorites`);
  }

  toggleFavorite(postId: number): Observable<{postId: number; favorited: boolean}> {
    return this.http.post<{postId: number; favorited: boolean}>(`${this.API}/posts/${postId}/favorite`, null);
  }
}
