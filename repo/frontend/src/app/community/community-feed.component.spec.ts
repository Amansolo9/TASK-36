import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CommunityFeedComponent } from './community-feed.component';
import { CommunityApiService } from '../core/services/community.service';
import { of } from 'rxjs';

describe('CommunityFeedComponent', () => {
  let component: CommunityFeedComponent;
  let fixture: ComponentFixture<CommunityFeedComponent>;
  let apiSpy: jasmine.SpyObj<CommunityApiService>;

  beforeEach(async () => {
    apiSpy = jasmine.createSpyObj('CommunityApiService', [
      'getFeed', 'getByTopic', 'getFollowedTopics', 'getMyFavorites',
      'getFollowingAuthors', 'followAuthor', 'unfollowAuthor',
      'createPostOffline', 'voteOffline', 'addCommentOffline',
      'getComments', 'followTopic', 'unfollowTopic', 'toggleFavorite'
    ]);
    apiSpy.getFeed.and.returnValue(of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }));
    apiSpy.getFollowedTopics.and.returnValue(of([]));
    apiSpy.getMyFavorites.and.returnValue(of([]));
    apiSpy.getFollowingAuthors.and.returnValue(of([10, 20]));

    await TestBed.configureTestingModule({
      imports: [CommunityFeedComponent],
      providers: [
        { provide: CommunityApiService, useValue: apiSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CommunityFeedComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load followed authors on init', () => {
    expect(apiSpy.getFollowingAuthors).toHaveBeenCalled();
    expect(component.followedAuthors().has(10)).toBeTrue();
    expect(component.followedAuthors().has(20)).toBeTrue();
  });

  it('isFollowing returns true for followed author', () => {
    expect(component.isFollowing(10)).toBeTrue();
    expect(component.isFollowing(99)).toBeFalse();
  });

  it('toggleFollowAuthor follows an unfollowed author', () => {
    apiSpy.followAuthor.and.returnValue(of(void 0));

    component.toggleFollowAuthor(99);

    expect(apiSpy.followAuthor).toHaveBeenCalledWith(99);
  });

  it('toggleFollowAuthor unfollows a followed author', () => {
    apiSpy.unfollowAuthor.and.returnValue(of(void 0));

    component.toggleFollowAuthor(10); // 10 is already followed

    expect(apiSpy.unfollowAuthor).toHaveBeenCalledWith(10);
  });

  it('following state updates after follow', () => {
    apiSpy.followAuthor.and.returnValue(of(void 0));

    component.toggleFollowAuthor(50);

    expect(component.followedAuthors().has(50)).toBeTrue();
  });

  it('following state updates after unfollow', () => {
    apiSpy.unfollowAuthor.and.returnValue(of(void 0));

    component.toggleFollowAuthor(10); // unfollow author 10

    expect(component.followedAuthors().has(10)).toBeFalse();
  });

  it('should preserve topic follow functionality', () => {
    apiSpy.followTopic.and.returnValue(of(void 0));

    component.newFollowTopic = 'angular';
    component.followTopic();

    expect(apiSpy.followTopic).toHaveBeenCalledWith('angular');
  });
});
