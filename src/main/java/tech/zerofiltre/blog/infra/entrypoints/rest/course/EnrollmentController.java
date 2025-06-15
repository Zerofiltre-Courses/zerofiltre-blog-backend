package tech.zerofiltre.blog.infra.entrypoints.rest.course;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.zerofiltre.blog.domain.FinderRequest;
import tech.zerofiltre.blog.domain.Page;
import tech.zerofiltre.blog.domain.article.model.Status;
import tech.zerofiltre.blog.domain.course.*;
import tech.zerofiltre.blog.domain.course.features.enrollment.*;
import tech.zerofiltre.blog.domain.course.model.Certificate;
import tech.zerofiltre.blog.domain.course.model.Course;
import tech.zerofiltre.blog.domain.course.model.Enrollment;
import tech.zerofiltre.blog.domain.error.ForbiddenActionException;
import tech.zerofiltre.blog.domain.error.ResourceNotFoundException;
import tech.zerofiltre.blog.domain.error.ZerofiltreException;
import tech.zerofiltre.blog.domain.purchase.PurchaseProvider;
import tech.zerofiltre.blog.domain.sandbox.SandboxProvider;
import tech.zerofiltre.blog.domain.user.UserProvider;
import tech.zerofiltre.blog.domain.user.features.UserNotFoundException;
import tech.zerofiltre.blog.domain.user.model.User;
import tech.zerofiltre.blog.infra.entrypoints.rest.SecurityContextManager;

import java.io.ByteArrayInputStream;

@RestController
@RequestMapping("/enrollment")
public class EnrollmentController {

    private final SecurityContextManager securityContextManager;
    private final Enroll enroll;
    private final Suspend suspend;
    private final CompleteLesson completeLesson;
    private final FindEnrollment findEnrollment;
    private final SandboxProvider sandboxProvider;


    private final GenerateCertificate generateCertificate;


    public EnrollmentController(
            EnrollmentProvider enrollmentProvider,
            CourseProvider courseProvider,
            UserProvider userProvider,
            SecurityContextManager securityContextManager,
            LessonProvider lessonProvider,
            ChapterProvider chapterProvider,
            SandboxProvider sandboxProvider,
            PurchaseProvider purchaseProvider,
            CertificateProvider certificateProvider) {
        this.securityContextManager = securityContextManager;
        this.sandboxProvider = sandboxProvider;
        enroll = new Enroll(enrollmentProvider, courseProvider, userProvider, chapterProvider, sandboxProvider, purchaseProvider);
        suspend = new Suspend(enrollmentProvider, chapterProvider, purchaseProvider, sandboxProvider);
        completeLesson = new CompleteLesson(enrollmentProvider, lessonProvider, chapterProvider, courseProvider);
        findEnrollment = new FindEnrollment(enrollmentProvider, courseProvider, chapterProvider);
        generateCertificate = new GenerateCertificate(enrollmentProvider, certificateProvider);
    }

    @PostMapping("/provision")
    public void provision(@RequestParam String fullName, @RequestParam String email) throws ZerofiltreException {
        sandboxProvider.initialize(fullName, email);
    }

    @PostMapping
    public Enrollment enroll(@RequestParam long courseId) throws ZerofiltreException {
        return enroll.execute(securityContextManager.getAuthenticatedUser().getId(), courseId);

    }

    @DeleteMapping
    public void unEnroll(@RequestParam long courseId) throws ZerofiltreException {
        suspend.execute(securityContextManager.getAuthenticatedUser().getId(), courseId);
    }

    @PatchMapping("/complete")
    public Enrollment completeLesson(@RequestParam long lessonId, @RequestParam long courseId) throws ZerofiltreException {
        return completeLesson.execute(courseId, lessonId, securityContextManager.getAuthenticatedUser().getId(), true);
    }

    @PatchMapping("/uncomplete")
    public Enrollment unCompleteLesson(@RequestParam long lessonId, @RequestParam long courseId) throws ZerofiltreException {
        return completeLesson.execute(courseId, lessonId, securityContextManager.getAuthenticatedUser().getId(), false);
    }


    @GetMapping
    public Enrollment getEnrollment(@RequestParam long courseId, @RequestParam long userId) throws ResourceNotFoundException, ForbiddenActionException {
        User executor = securityContextManager.getAuthenticatedUser();
        return findEnrollment.of(courseId, userId, executor);
    }

    @GetMapping("/user")
    Page<Course> coursesOfEnrollment(
            @RequestParam int pageNumber,
            @RequestParam int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String tag) throws UserNotFoundException {
        FinderRequest request = new FinderRequest();
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);
        request.setUser(securityContextManager.getAuthenticatedUser());
        request.setTag(tag);
        if (filter != null) {
            filter = filter.toUpperCase();
            request.setFilter(FinderRequest.Filter.valueOf(filter));
        }
        if (status != null) {
            status = status.toUpperCase();
            request.setStatus(Status.valueOf(status));
        }
        return findEnrollment.of(request);
    }

    @GetMapping("/certificate")
    public ResponseEntity<InputStreamResource> giveCertificateByCourseId(@RequestParam long courseId) throws ZerofiltreException {
        User user = securityContextManager.getAuthenticatedUser();
        Certificate certificate = generateCertificate.get(user, courseId);

        byte[] content = certificate.getContent();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(content);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + certificate.getName());

        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(content.length)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(byteArrayInputStream));
    }

}
