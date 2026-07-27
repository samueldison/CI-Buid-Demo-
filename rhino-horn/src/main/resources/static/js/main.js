$(document).ready(function() {
  
  // Smooth scrolling for anchor links
  $('a[href^="#"]').on('click', function(e) {
    const target = $(this.getAttribute('href'));
    if (target.length) {
      e.preventDefault();
      $('html, body').stop().animate({
        scrollTop: target.offset().top - 80
      }, 800, 'swing');
    }
  });

  // Back to top button functionality
  const backToTopBtn = $('#backToTop');
  
  $(window).scroll(function() {
    if ($(this).scrollTop() > 300) {
      backToTopBtn.addClass('show');
    } else {
      backToTopBtn.removeClass('show');
    }
  });

  backToTopBtn.on('click', function(e) {
    e.preventDefault();
    $('html, body').animate({ scrollTop: 0 }, 800, 'swing');
  });

  // Intersection Observer for fade-in animations
  const observerOptions = {
    threshold: 0.1,
    rootMargin: '0px 0px -50px 0px'
  };

  const observer = new IntersectionObserver(function(entries) {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.style.opacity = '1';
        entry.target.style.transform = 'translateY(0)';
      }
    });
  }, observerOptions);

  // Observe all fade-in elements
  document.querySelectorAll('.fade-in').forEach(el => {
    el.style.opacity = '0';
    el.style.transform = 'translateY(20px)';
    el.style.transition = 'opacity 0.6s ease, transform 0.6s ease';
    observer.observe(el);
  });

  // Add loading effect for images
  $('img.tool-logo').on('load', function() {
    $(this).addClass('loaded');
  });

  // Navbar background change on scroll
  $(window).scroll(function() {
    if ($(this).scrollTop() > 50) {
      $('.navbar').css('box-shadow', '0 4px 20px rgba(0,0,0,0.15)');
    } else {
      $('.navbar').css('box-shadow', '0 2px 10px rgba(0,0,0,0.1)');
    }
  });

});
