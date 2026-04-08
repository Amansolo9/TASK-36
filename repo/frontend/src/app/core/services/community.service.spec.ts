import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CommunityApiService } from './community.service';
import { OfflineQueueService } from './offline-queue.service';

describe('CommunityApiService', () => {
  let service: CommunityApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        CommunityApiService,
        { provide: OfflineQueueService, useValue: { enqueue: jasmine.createSpy('enqueue') } }
      ]
    });
    service = TestBed.inject(CommunityApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  // ─── Author Following ───

  describe('followAuthor', () => {
    it('should POST to /api/community/users/{userId}/follow', () => {
      service.followAuthor(42).subscribe();
      const req = httpMock.expectOne('/api/community/users/42/follow');
      expect(req.request.method).toBe('POST');
      req.flush(null);
    });
  });

  describe('unfollowAuthor', () => {
    it('should DELETE /api/community/users/{userId}/follow', () => {
      service.unfollowAuthor(42).subscribe();
      const req = httpMock.expectOne('/api/community/users/42/follow');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('getFollowingAuthors', () => {
    it('should GET /api/community/following and return user IDs', () => {
      service.getFollowingAuthors().subscribe(ids => {
        expect(ids).toEqual([1, 2, 3]);
      });
      const req = httpMock.expectOne('/api/community/following');
      expect(req.request.method).toBe('GET');
      req.flush([1, 2, 3]);
    });
  });

  // ─── Topic Following (existing) ───

  describe('followTopic', () => {
    it('should POST to /api/community/topics/{topic}/follow', () => {
      service.followTopic('angular').subscribe();
      const req = httpMock.expectOne('/api/community/topics/angular/follow');
      expect(req.request.method).toBe('POST');
      req.flush(null);
    });
  });

  describe('unfollowTopic', () => {
    it('should DELETE /api/community/topics/{topic}/follow', () => {
      service.unfollowTopic('angular').subscribe();
      const req = httpMock.expectOne('/api/community/topics/angular/follow');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  // ─── Favorites ───

  describe('toggleFavorite', () => {
    it('should POST to /api/community/posts/{postId}/favorite', () => {
      service.toggleFavorite(5).subscribe(res => {
        expect(res.postId).toBe(5);
        expect(res.favorited).toBeTrue();
      });
      const req = httpMock.expectOne('/api/community/posts/5/favorite');
      expect(req.request.method).toBe('POST');
      req.flush({ postId: 5, favorited: true });
    });
  });

  // ─── Feed ───

  describe('getFeed', () => {
    it('should GET /api/community/posts with pagination', () => {
      service.getFeed(0, 20).subscribe();
      const req = httpMock.expectOne(r => r.url === '/api/community/posts');
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('20');
      req.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
    });
  });
});
