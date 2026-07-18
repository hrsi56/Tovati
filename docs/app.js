const header = document.querySelector("[data-header]");
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const updateHeader = () => {
  header?.classList.toggle("scrolled", window.scrollY > 20);
};

updateHeader();
window.addEventListener("scroll", updateHeader, { passive: true });

const reveals = document.querySelectorAll(".reveal");

if (reduceMotion || !("IntersectionObserver" in window)) {
  reveals.forEach((item) => item.classList.add("visible"));
} else {
  const revealObserver = new IntersectionObserver(
    (entries, observer) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add("visible");
        observer.unobserve(entry.target);
      });
    },
    { threshold: 0.12, rootMargin: "0px 0px -35px" },
  );

  reveals.forEach((item) => revealObserver.observe(item));
}

const screens = {
  diary: {
    src: "assets/app-diary.png",
    alt: "יומן המחזור של טובתי מסודר לפי שבועות וימי מחזור",
    title: "לחצי על יום",
    caption: "וראי את כל המדידות, הסימנים וההערות",
  },
  chart: {
    src: "assets/app-chart.png",
    alt: "גרף חום השחר של טובתי מציג את המגמה והסיכום",
    title: "המגמה שלך מול העיניים",
    caption: "מדידות, חלונות והערכה בתצוגה אחת ברורה",
  },
  entry: {
    src: "assets/app-entry.png",
    alt: "מסך הרישום המהיר של טובתי",
    title: "כמה בחירות ושמרת",
    caption: "המידע היומי נכנס בלי טבלאות ובלי עומס",
  },
};

const featurePhone = document.querySelector(".feature-phone");
const featureImage = document.querySelector("[data-feature-image]");
const featureCaption = document.querySelector("[data-feature-caption]");
const featureTabs = document.querySelectorAll("[data-screen]");

featureTabs.forEach((tab) => {
  tab.addEventListener("click", () => {
    const screen = screens[tab.dataset.screen];
    if (!screen || tab.classList.contains("active")) return;

    featureTabs.forEach((item) => {
      const isActive = item === tab;
      item.classList.toggle("active", isActive);
      item.setAttribute("aria-selected", String(isActive));
    });

    featurePhone?.classList.add("changing");

    window.setTimeout(() => {
      if (featureImage) {
        featureImage.src = screen.src;
        featureImage.alt = screen.alt;
      }
      if (featureCaption) {
        featureCaption.innerHTML = `<strong>${screen.title}</strong><span>${screen.caption}</span>`;
      }
      featurePhone?.classList.remove("changing");
    }, reduceMotion ? 0 : 180);
  });
});
