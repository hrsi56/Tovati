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

const installDemo = document.querySelector("[data-install-demo]");

if (installDemo) {
  const installSteps = [...installDemo.querySelectorAll("[data-install-step]")];
  const installScreens = [...installDemo.querySelectorAll("[data-install-screen]")];
  const installDots = [...installDemo.querySelectorAll(".demo-progress i")];
  const installToggle = installDemo.querySelector("[data-install-toggle]");
  const installToggleLabel = installDemo.querySelector("[data-install-toggle-label]");
  const installToggleIcon = installDemo.querySelector(".demo-toggle-icon");
  const installStatus = installDemo.querySelector("[data-install-status]");
  const installLabels = [
    "הורידי ופתחי",
    "אשרי למקור הזה",
    "פתחי את הפרטים הנוספים",
    "התקיני בכל זאת",
  ];

  let installIndex = 0;
  let installTimer;
  let installVisible = false;
  let installPaused = reduceMotion;

  const updateInstallToggle = () => {
    if (!installToggle || !installToggleLabel || !installToggleIcon) return;

    if (reduceMotion) {
      installToggle.hidden = true;
      return;
    }

    installToggle.setAttribute("aria-pressed", String(installPaused));
    installToggle.setAttribute(
      "aria-label",
      installPaused ? "הפעילי את הדגמת ההתקנה" : "עצרי את הדגמת ההתקנה",
    );
    installToggleLabel.textContent = installPaused ? "הפעילי" : "עצרי";
    installToggleIcon.textContent = installPaused ? "▶" : "Ⅱ";
  };

  const showInstallStep = (nextIndex, announce = false) => {
    installIndex = (nextIndex + installSteps.length) % installSteps.length;

    installSteps.forEach((step, index) => {
      const isActive = index === installIndex;
      step.classList.toggle("active", isActive);
      step.classList.toggle("complete", index < installIndex);
      step.setAttribute("aria-selected", String(isActive));
      step.tabIndex = isActive ? 0 : -1;
    });

    installScreens.forEach((screen, index) => {
      const isActive = index === installIndex;
      screen.classList.toggle("active", isActive);
      screen.setAttribute("aria-hidden", String(!isActive));
    });

    installDots.forEach((dot, index) => {
      dot.classList.toggle("active", index === installIndex);
    });

    if (installStatus) {
      installStatus.setAttribute("aria-live", announce ? "polite" : "off");
      installStatus.textContent = `שלב ${installIndex + 1} מתוך ${installSteps.length}: ${installLabels[installIndex]}`;
    }
  };

  const stopInstallTimer = () => {
    window.clearInterval(installTimer);
    installTimer = undefined;
  };

  const startInstallTimer = () => {
    stopInstallTimer();
    if (installPaused || !installVisible || document.hidden) return;

    installTimer = window.setInterval(() => {
      showInstallStep(installIndex + 1);
    }, 4200);
  };

  installSteps.forEach((step, index) => {
    step.addEventListener("click", () => {
      installPaused = true;
      showInstallStep(index, true);
      updateInstallToggle();
      stopInstallTimer();
    });

    step.addEventListener("keydown", (event) => {
      let nextIndex;

      if (event.key === "ArrowDown" || event.key === "ArrowLeft") {
        nextIndex = installIndex + 1;
      } else if (event.key === "ArrowUp" || event.key === "ArrowRight") {
        nextIndex = installIndex - 1;
      } else if (event.key === "Home") {
        nextIndex = 0;
      } else if (event.key === "End") {
        nextIndex = installSteps.length - 1;
      } else {
        return;
      }

      event.preventDefault();
      installPaused = true;
      showInstallStep(nextIndex, true);
      updateInstallToggle();
      stopInstallTimer();
      installSteps[installIndex]?.focus();
    });
  });

  installToggle?.addEventListener("click", () => {
    installPaused = !installPaused;
    updateInstallToggle();
    startInstallTimer();
  });

  if ("IntersectionObserver" in window) {
    const installObserver = new IntersectionObserver(
      ([entry]) => {
        installVisible = entry.isIntersecting;
        if (installVisible) {
          startInstallTimer();
        } else {
          stopInstallTimer();
        }
      },
      { threshold: 0.2 },
    );

    installObserver.observe(installDemo);
  } else {
    installVisible = true;
    startInstallTimer();
  }

  document.addEventListener("visibilitychange", startInstallTimer);
  updateInstallToggle();
  showInstallStep(0);
}
