---
title: "Elements and components take values and callbacks only"
scope: "file"
path: ["**/designsystem/**", "**/presentation/**"]
severity_min: "high"
---

## Instructions

UI follows elements, then components, then screens. Elements and components, in the design system
or in `feature/<name>/presentation/components`, take plain values and callbacks. Flag these:

- An element or component that takes or creates a ViewModel, repository, navigation controller,
  network client, database, or coroutine scope for loading data.
- An element that uses a component, or a component that uses a screen.
- Business rules inside a composable, such as filtering, sorting, date maths, or protocol
  decisions. Those belong in domain code or a ViewModel, and the composable receives the result.
- A domain model from an app passed into a Meta design-system component. Apps pass display values
  through a feature adapter.

Screen entry points may connect a ViewModel and navigation. Screen content still takes UI state and
emits actions.

## Examples

### Bad example

```kotlin
@Composable
fun MessageRow(viewModel: InboxViewModel, id: String) {
    val message = viewModel.message(id)
    // ...
}
```

### Good example

```kotlin
@Composable
fun MessageRow(sender: String, subject: String, onClick: () -> Unit, modifier: Modifier = Modifier)
```
