package com.ohgiraffers.springeagles.jstBlog.posts.controller;

import com.ohgiraffers.springeagles.global.user.repository.UserEntity;
import com.ohgiraffers.springeagles.global.user.service.CustomUserDetailsService;
import com.ohgiraffers.springeagles.jstBlog.comment.repository.STCommentEntity;
import com.ohgiraffers.springeagles.jstBlog.comment.service.STCommentService;
import com.ohgiraffers.springeagles.jstBlog.likes.service.STLikesService;
import com.ohgiraffers.springeagles.jstBlog.posts.dto.STPostsDTO;
import com.ohgiraffers.springeagles.jstBlog.posts.repository.STPostsEntity;
import com.ohgiraffers.springeagles.jstBlog.posts.service.STPostsService;
import com.ohgiraffers.springeagles.jstBlog.userIntro.service.UserIntroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/stj/blog")
public class STPostsController {

    private final STPostsService stPostsService;
    private final UserIntroService userIntroService;
    private final STCommentService stCommentService;
    private final STLikesService stLikesService;
    private final CustomUserDetailsService userService;

    @Autowired
    public STPostsController(STPostsService stPostsService, UserIntroService userIntroService, STCommentService stCommentService, STLikesService stLikesService, CustomUserDetailsService userService) {
        this.stPostsService = stPostsService;
        this.userIntroService = userIntroService;
        this.stCommentService = stCommentService;
        this.stLikesService = stLikesService;
        this.userService = userService;
    }

    @GetMapping("/posts")
    public String getAllPosts(@RequestParam(value = "postId", required = false) Integer postId, Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        UserEntity user = userService.findByUserName(username).orElseThrow(
                () -> new IllegalArgumentException("사용자를 찾을 수 없음"));

        Integer userId = user.getUserId();
        Map<String, Integer> tagCounts = stPostsService.calculateTagCounts();
        String introContent = "자기소개를 입력해주세요.";

        List<STPostsEntity> posts = stPostsService.getAllPosts();
        Map<Integer, Integer> commentCounts = new HashMap<>();
        Map<Integer, Integer> likeCounts = new HashMap<>();

        for (STPostsEntity post : posts) {
            int commentCount = stPostsService.getCommentCountByPostId(post.getPostId());
            commentCounts.put(post.getPostId(), commentCount);

            int likeCount = stLikesService.getLikesCountByPostId(post.getPostId());
            likeCounts.put(post.getPostId(), likeCount);
        }

        model.addAttribute("posts", posts);
        model.addAttribute("commentCounts", commentCounts);
        model.addAttribute("likeCounts", likeCounts);
        model.addAttribute("tagCounts", tagCounts);
        model.addAttribute("username", username);
        model.addAttribute("introContent", introContent);
        model.addAttribute("currentPage", "mainPage");

        return "jst_blog/blogPost";
    }

    @GetMapping("/post/{postId}")
    public String getPostById(@PathVariable("postId") Integer postId, Model model) {
        STPostsEntity post = stPostsService.getPostById(postId).orElse(null);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        if (post == null) {
            return "redirect:/stj/blog/posts";
        }

        List<STCommentEntity> comments = stCommentService.getCommentsByPost(postId);

        UserEntity user = userService.findByUserName(username).orElseThrow(
                () -> new IllegalArgumentException("사용자를 찾을 수 없음"));

        Integer userId = user.getUserId();
        boolean isLiked = stLikesService.getLikeStatuses(userId).getOrDefault(postId, false);

        model.addAttribute("post", post);
        model.addAttribute("selectedId", postId);
        model.addAttribute("comments", comments);
        model.addAttribute("username", username);
        model.addAttribute("currentPage", "readPage");
        model.addAttribute("isLiked", isLiked);
        return "jst_blog/blogPost";
    }

    @GetMapping("/edit")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String showCreateForm(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("postDTO", new STPostsDTO());
        model.addAttribute("username", authentication.getName());
        model.addAttribute("currentPage", "editPage");
        return "jst_blog/blogPost";
    }

    @PostMapping("/edit")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<String> responseEdit(@RequestBody STPostsDTO stPostsDTO) {
        try {
            stPostsService.createPost(stPostsDTO);
            return ResponseEntity.ok("게시물 작성이 완료되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("게시물 작성 중 오류가 발생했습니다.");
        }
    }

    @GetMapping("/update/{postId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String showUpdateForm(@PathVariable("postId") Integer postId, Model model) {
        STPostsEntity postEntity = stPostsService.getPostById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시물을 찾을 수 없습니다. ID: " + postId));
        STPostsDTO postDTO = new STPostsDTO(postEntity);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        model.addAttribute("postDTO", postDTO);
        model.addAttribute("username", authentication.getName());
        model.addAttribute("currentPage", "updatePage");
        return "jst_blog/blogPost";
    }

    @PostMapping("/update/{postId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public RedirectView updatePost(@PathVariable("postId") Integer postId, @RequestBody STPostsDTO stPostsDTO) {
        try {
            stPostsDTO.setPostId(postId);
            stPostsService.updatePost(stPostsDTO);
            return new RedirectView("/stj/blog/posts", true);
        } catch (Exception e) {
            return new RedirectView("/error", true);
        }
    }

    @PostMapping("/delete/{postId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public RedirectView deletePost(@PathVariable("postId") Integer postId) {
        try {
            stPostsService.deletePost(postId);
            return new RedirectView("/stj/blog/posts", true);
        } catch (Exception e) {
            return new RedirectView("/error", true);
        }
    }
}
