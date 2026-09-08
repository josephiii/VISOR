# Section 508 Accessibility Guidelines
*Research by Jaurelus*

Section 508 is documentation that lays out valid guidelines for digital development such that individuals with disabilities are still able to access Information and Communication Technology (ICT). Section 508 specifically regulates the federal government and the ICT they own, operate, or manage; it also provides an outline for all application developers to make their product accessible to all.

The guidelines for creating an accessible application fall into one of four categories represented by the acronym **POUR**.

---

## P - Perceivable

Content must be perceivable, or able to be processed by one's senses. If information or content is not able to be perceived by a user then it basically does not exist to that user. For example, if a blind user uses an application with no screen reader or any audible function, the information on that site is useless to that user. The goal is to make all content as perceivable as possible to all possible audiences. With an app targeted for an audience of low vision users, it's not an option to omit this kind of accessibility.

- Screen Reader Support
- Zoom (Magnifier)
- Color contrast
- Alt text on images
- Captions on videos
- Adaptable layouts without losing info (e.g. larger text)
- Flashing - there should be no more than 3 flashes per second

---

## O - Operable

The ability (or lack thereof) for a user to control and navigate an application determines how operable the application is. Users need to be able to operate the application, so the system cannot require actions that a user cannot perform.

- Navigable (size of buttons)
- Orientation (support both portrait and landscape)
- Accessible (complex gestures like pinching must have an easier alternative)
- Screen reader (they rely on swiping, so layout must be considerate)

---

## U - Understandable

The content and interface must behave in a predictable and clear manner.

- Consistent Navigation
- Error Identification - clearly define errors; don't just make them red
- Labels on inputs
- Readable - declare language so screen readers know how to pronounce content

---

## R - Robust

The application must work with assistive technologies as they evolve.

- Use proper accessibility APIs
- Handle OS settings for accessibility
- Explicitly state each component's name, role, value, and state

---

## Notes on Important Topics

### Color & Contrast

- Text and image text must have at least a **4.5:1 contrast ratio**, with the following exceptions:
  1. **Large scale text** - minimum 3:1
  2. **Incidental** - text is decorative or part of an image
  3. **Logos**
- Use the lightest color when using a color gradient
- Large text must be at least **18pt**, or **14pt if bold**, to provide proper contrast
- Use color to convey meaning

### Text

- All text must be resizable except captions and alt image text
- Text must be resizable up to **200%** without losing content or functionality
- Zooming on the screen itself should not cause any loss in content or functionality
- Use `em` units to define height and width in a way that keeps them proportional
- Use headings and labels for structure and to describe content for screen readers (be mindful of the difference between `h1` and `h6`)
- Use language a screen reader can pronounce - avoid slang
- All non-text content must have a text alternative

### Images

- All images need a text alternative
- Consider: *what text would I put here instead of this image?*

### Keyboard / Focus

> *(Notes to be added)*

### Code Considerations

> *(Notes to be added)*

### Forms

> *(Notes to be added)*

### Frames & iFrames

- Every frame/iFrame must have a defined: **name, role, states, properties, values,** and **notifications**

### Navigation

- Make sure directions don't solely rely on sensory characteristics (e.g. "the red box" or "on the left side")

---

## Resources

- Website that filters features required for specific disabilities *(link to be added)*
