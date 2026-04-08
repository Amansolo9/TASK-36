import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CommunityApiService } from '../core/services/community.service';
import { PostResponse } from '../core/models/community.model';

@Component({
  selector: 'app-community-feed',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="community-page">
      <div class="sidebar">
        <div class="create-post">
          <h3>New Post</h3>
          <input [(ngModel)]="newTitle" placeholder="Title" maxlength="200">
          <textarea [(ngModel)]="newBody" placeholder="What's on your mind?" rows="3"></textarea>
          <input [(ngModel)]="newTopic" placeholder="Topic (optional)">
          <button (click)="createPost()" [disabled]="!newTitle || !newBody">Post</button>
        </div>

        <div class="topics-section">
          <h3>Followed Topics</h3>
          @for (topic of followedTopics(); track topic) {
            <div class="topic-tag">
              <span (click)="filterByTopic(topic)">{{ topic }}</span>
              <button class="btn-unfollow" (click)="unfollowTopic(topic)">&times;</button>
            </div>
          }
          <div class="follow-input">
            <input [(ngModel)]="newFollowTopic" placeholder="Follow a topic...">
            <button (click)="followTopic()" [disabled]="!newFollowTopic">+</button>
          </div>
        </div>
      </div>

      <div class="feed">
        @if (activeTopic()) {
          <div class="filter-bar">
            Showing: <strong>{{ activeTopic() }}</strong>
            <button (click)="clearFilter()">Show All</button>
          </div>
        }

        @for (post of posts(); track post.id) {
          <div class="post-card">
            <div class="vote-col">
              <button class="vote-btn" [class.active]="post.currentUserVote === 'UPVOTE'"
                      (click)="vote(post.id, 'UPVOTE')">&#9650;</button>
              <span class="net-votes" [class.positive]="post.netVotes > 0"
                    [class.negative]="post.netVotes < 0">{{ post.netVotes }}</span>
              <button class="vote-btn" [class.active]="post.currentUserVote === 'DOWNVOTE'"
                      (click)="vote(post.id, 'DOWNVOTE')">&#9660;</button>
            </div>
            <div class="post-content">
              <div class="post-header">
                <strong>{{ post.title }}</strong>
                @if (post.topic) {
                  <span class="topic-badge" (click)="filterByTopic(post.topic!)">{{ post.topic }}</span>
                }
              </div>
              <p class="post-body">{{ post.body }}</p>
              <div class="post-meta">
                <span class="author-info">
                  by {{ post.authorName }}
                  <button class="btn-follow"
                          [class.following]="isFollowing(post.authorId)"
                          (click)="toggleFollowAuthor(post.authorId)">
                    {{ isFollowing(post.authorId) ? 'Following' : 'Follow' }}
                  </button>
                </span>
                <span>{{ post.createdAt | date:'short' }}</span>
                <span>{{ post.commentCount }} comments</span>
                <button class="btn-fav" [class.fav-active]="isFavorited(post.id)" (click)="toggleFavorite(post.id)">
                  {{ isFavorited(post.id) ? '&#9829;' : '&#9825;' }}
                </button>
              </div>
              <div class="comment-section">
                <button class="btn-comments" (click)="toggleComments(post.id)">
                  {{ expandedPost() === post.id ? 'Hide' : 'Show' }} Comments
                </button>
                @if (expandedPost() === post.id) {
                  <div class="comments-list">
                    @for (comment of comments(); track comment.id) {
                      <div class="comment">
                        <strong>{{ comment.authorName }}</strong>: {{ comment.body }}
                        <small>{{ comment.createdAt | date:'short' }}</small>
                      </div>
                    }
                    <div class="add-comment">
                      <input [(ngModel)]="newComment" placeholder="Add a comment...">
                      <button (click)="addComment(post.id)" [disabled]="!newComment">Reply</button>
                    </div>
                  </div>
                }
              </div>
            </div>
          </div>
        }

        @if (posts().length === 0) {
          <p class="empty">No posts yet. Be the first to share!</p>
        }
      </div>
    </div>
  `,
  styles: [`
    .community-page { max-width: 1000px; margin: 2rem auto; padding: 0 1rem; display: flex; gap: 1.5rem; }
    .sidebar { width: 280px; flex-shrink: 0; }
    .feed { flex: 1; }
    .create-post, .topics-section {
      background: white; padding: 1.25rem; border-radius: 8px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 1rem;
    }
    .create-post h3, .topics-section h3 { margin-top: 0; margin-bottom: 0.75rem; }
    .create-post input, .create-post textarea {
      width: 100%; padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px;
      margin-bottom: 0.5rem; box-sizing: border-box; font-family: inherit;
    }
    .create-post button {
      width: 100%; padding: 0.5rem; background: #1976d2; color: white;
      border: none; border-radius: 4px; cursor: pointer;
    }
    .create-post button:disabled { opacity: 0.5; }
    .topic-tag {
      display: inline-flex; align-items: center; gap: 4px;
      background: #e3f2fd; color: #1565c0; padding: 0.2rem 0.5rem;
      border-radius: 12px; margin: 0.2rem; font-size: 0.85rem; cursor: pointer;
    }
    .btn-unfollow { background: none; border: none; color: #c62828; cursor: pointer; font-size: 1rem; }
    .follow-input { display: flex; gap: 0.25rem; margin-top: 0.5rem; }
    .follow-input input { flex: 1; padding: 0.4rem; border: 1px solid #ddd; border-radius: 4px; }
    .follow-input button {
      padding: 0.4rem 0.6rem; background: #1976d2; color: white;
      border: none; border-radius: 4px; cursor: pointer;
    }
    .filter-bar {
      background: #fff3e0; padding: 0.5rem 1rem; border-radius: 4px; margin-bottom: 1rem;
      display: flex; align-items: center; gap: 0.5rem;
    }
    .filter-bar button { background: none; border: 1px solid #e65100; color: #e65100; padding: 0.2rem 0.5rem; border-radius: 4px; cursor: pointer; }
    .post-card {
      background: white; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);
      margin-bottom: 0.75rem; padding: 1rem; display: flex; gap: 1rem;
    }
    .vote-col { display: flex; flex-direction: column; align-items: center; gap: 2px; min-width: 40px; }
    .vote-btn { background: none; border: none; cursor: pointer; font-size: 1.2rem; color: #999; padding: 2px; }
    .vote-btn.active { color: #1976d2; }
    .net-votes { font-weight: 700; font-size: 1.1rem; }
    .net-votes.positive { color: #2e7d32; }
    .net-votes.negative { color: #c62828; }
    .post-content { flex: 1; }
    .post-header { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem; }
    .topic-badge {
      background: #f3e5f5; color: #7b1fa2; padding: 0.1rem 0.5rem;
      border-radius: 12px; font-size: 0.75rem; cursor: pointer;
    }
    .post-body { margin: 0.5rem 0; color: #333; }
    .post-meta { color: #888; font-size: 0.85rem; display: flex; gap: 1rem; }
    .btn-comments { background: none; border: none; color: #1976d2; cursor: pointer; margin-top: 0.5rem; padding: 0; }
    .comments-list { margin-top: 0.5rem; border-top: 1px solid #eee; padding-top: 0.5rem; }
    .comment { padding: 0.4rem 0; border-bottom: 1px solid #f5f5f5; }
    .comment small { color: #888; margin-left: 0.5rem; }
    .add-comment { display: flex; gap: 0.5rem; margin-top: 0.5rem; }
    .add-comment input { flex: 1; padding: 0.4rem; border: 1px solid #ddd; border-radius: 4px; }
    .add-comment button {
      padding: 0.4rem 0.8rem; background: #1976d2; color: white;
      border: none; border-radius: 4px; cursor: pointer;
    }
    .empty { text-align: center; color: #888; }
    .btn-fav { background: none; border: none; cursor: pointer; font-size: 1.2rem; color: #ccc; padding: 0; }
    .btn-fav.fav-active { color: #e53935; }
    .author-info { display: inline-flex; align-items: center; gap: 0.4rem; }
    .btn-follow {
      font-size: 0.75rem; padding: 0.1rem 0.5rem; border-radius: 12px;
      border: 1px solid #1976d2; color: #1976d2; background: white;
      cursor: pointer; transition: all 0.15s ease;
    }
    .btn-follow:hover { background: #e3f2fd; }
    .btn-follow.following { background: #1976d2; color: white; border-color: #1976d2; }
  `]
})
export class CommunityFeedComponent implements OnInit {
  posts = signal<PostResponse[]>([]);
  comments = signal<{ id: number; postId: number; authorId: number; authorName: string; body: string; createdAt: string }[]>([]);
  followedTopics = signal<string[]>([]);
  followedAuthors = signal<Set<number>>(new Set());
  expandedPost = signal<number | null>(null);
  activeTopic = signal<string | null>(null);
  favoritedPosts = signal<Set<number>>(new Set());

  newTitle = '';
  newBody = '';
  newTopic = '';
  newComment = '';
  newFollowTopic = '';

  constructor(private api: CommunityApiService) {}

  ngOnInit(): void {
    this.loadFeed();
    this.api.getFollowedTopics().subscribe(t => this.followedTopics.set(t));
    this.api.getMyFavorites().subscribe(ids => this.favoritedPosts.set(new Set(ids)));
    this.api.getFollowingAuthors().subscribe(ids => this.followedAuthors.set(new Set(ids)));
  }

  loadFeed(): void {
    const topic = this.activeTopic();
    if (topic) {
      this.api.getByTopic(topic).subscribe(page => this.posts.set(page.content));
    } else {
      this.api.getFeed().subscribe(page => this.posts.set(page.content));
    }
  }

  async createPost(): Promise<void> {
    const result = await this.api.createPostOffline({ title: this.newTitle, body: this.newBody, topic: this.newTopic || undefined });
    if (result.outcome === 'synced') {
      this.loadFeed();
    }
    this.newTitle = '';
    this.newBody = '';
    this.newTopic = '';
  }

  async vote(postId: number, type: 'UPVOTE' | 'DOWNVOTE'): Promise<void> {
    const result = await this.api.voteOffline(postId, type);
    if (result.outcome === 'synced') {
      this.loadFeed();
    }
  }

  toggleComments(postId: number): void {
    if (this.expandedPost() === postId) {
      this.expandedPost.set(null);
      return;
    }
    this.expandedPost.set(postId);
    this.api.getComments(postId).subscribe(c => this.comments.set(c));
  }

  async addComment(postId: number): Promise<void> {
    const result = await this.api.addCommentOffline(postId, this.newComment);
    if (result.outcome === 'synced') {
      this.api.getComments(postId).subscribe(c => this.comments.set(c));
      this.posts.update(posts => posts.map(p =>
        p.id === postId ? { ...p, commentCount: p.commentCount + 1 } : p
      ));
    }
    this.newComment = '';
  }

  filterByTopic(topic: string): void {
    this.activeTopic.set(topic);
    this.loadFeed();
  }

  clearFilter(): void {
    this.activeTopic.set(null);
    this.loadFeed();
  }

  followTopic(): void {
    const topic = this.newFollowTopic.trim();
    if (!topic) return;
    this.api.followTopic(topic).subscribe(() => {
      this.followedTopics.update(t => [...t, topic]);
      this.newFollowTopic = '';
    });
  }

  unfollowTopic(topic: string): void {
    this.api.unfollowTopic(topic).subscribe(() => {
      this.followedTopics.update(t => t.filter(x => x !== topic));
    });
  }

  toggleFavorite(postId: number): void {
    this.api.toggleFavorite(postId).subscribe(res => {
      this.favoritedPosts.update(set => {
        const next = new Set(set);
        if (res.favorited) next.add(postId); else next.delete(postId);
        return next;
      });
    });
  }

  isFavorited(postId: number): boolean {
    return this.favoritedPosts().has(postId);
  }

  isFollowing(authorId: number): boolean {
    return this.followedAuthors().has(authorId);
  }

  toggleFollowAuthor(authorId: number): void {
    if (this.isFollowing(authorId)) {
      this.api.unfollowAuthor(authorId).subscribe(() => {
        this.followedAuthors.update(set => {
          const next = new Set(set);
          next.delete(authorId);
          return next;
        });
      });
    } else {
      this.api.followAuthor(authorId).subscribe(() => {
        this.followedAuthors.update(set => {
          const next = new Set(set);
          next.add(authorId);
          return next;
        });
      });
    }
  }
}
