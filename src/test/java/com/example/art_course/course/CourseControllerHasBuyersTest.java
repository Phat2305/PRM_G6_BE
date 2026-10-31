package com.example.art_course.course;

import com.example.art_course.course.controller.CourseController;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.service.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cờ hasBuyers đi hết đường từ service ra JSON mà FE đọc: đúng tên trường, chỉ đúng với giáo viên chủ khóa.
class CourseControllerHasBuyersTest {
    private final CourseService service = mock(CourseService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CourseController(service)).build();
    private final UUID teacherId = UUID.randomUUID();
    private final Course sold = Course.builder().id(UUID.randomUUID()).teacherId(teacherId).name("Đã bán")
            .status("PUBLISHED").build();
    private final Course unsold = Course.builder().id(UUID.randomUUID()).teacherId(teacherId).name("Chưa bán")
            .status("PUBLISHED").build();

    private UsernamePasswordAuthenticationToken login(UUID userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void teacherListCarriesHasBuyersPerCourseForTheOwner() throws Exception {
        when(service.getCoursesForViewer(teacherId, teacherId.toString())).thenReturn(List.of(sold, unsold));
        when(service.courseIdsWithBuyers(any())).thenReturn(Set.of(sold.getId()));

        mvc.perform(get("/api/courses/teacher/{id}", teacherId).principal(login(teacherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasBuyers").value(true))
                .andExpect(jsonPath("$[1].hasBuyers").value(false));
    }

    @Test
    void singleCourseCarriesHasBuyersForTheOwner() throws Exception {
        when(service.getCourseForViewer(sold.getId(), teacherId.toString())).thenReturn(sold);
        when(service.courseIdsWithBuyers(any())).thenReturn(Set.of(sold.getId()));

        mvc.perform(get("/api/courses/{id}", sold.getId()).principal(login(teacherId)))
                .andExpect(jsonPath("$.hasBuyers").value(true));
    }

    @Test
    void otherViewersNeverSeeHasBuyers() throws Exception {
        UUID stranger = UUID.randomUUID();
        when(service.getCoursesForViewer(teacherId, stranger.toString())).thenReturn(List.of(sold));
        // Dù service có biết khóa đã bán, người xem không phải chủ khóa thì không được hỏi và luôn nhận false.
        when(service.courseIdsWithBuyers(any())).thenReturn(Set.of(sold.getId()));

        mvc.perform(get("/api/courses/teacher/{id}", teacherId).principal(login(stranger)))
                .andExpect(jsonPath("$[0].hasBuyers").value(false));
    }
}
