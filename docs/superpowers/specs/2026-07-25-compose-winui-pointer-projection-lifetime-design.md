# Compose WinUI Pointer Projection Lifetime Design

## Goal

Stop the WinUI MPP sample from retaining native memory while the pointer moves.
The fix must cover the shared `winuiMain` pointer path used by the JVM and
MinGW targets without introducing ABI-slot workarounds, process-shell
fallbacks, or a separate WinUI input architecture.

## Confirmed Failure Model

The JVM sample reproduces the leak on the current `winui_dev` baseline. After
one full GC, 3,907 effective pointer inputs increased process private bytes
from 845.9 MB to 868.6 MB and working set from 471.0 MB to 492.4 MB. A second
full GC reduced the managed heap to approximately 22 MB, but the 22.7 MB native
increase remained after a further 15 seconds of idle time.

The high-frequency path creates owned WinRT projections on every routed
pointer event:

- `PointerRoutedEventArgs` and its lazily acquired default interface;
- the `PointerPoint` returned by `getCurrentPoint`;
- the point's `PointerPointProperties`;
- the collection returned by `getIntermediatePoints`;
- each `PointerPoint` read from that collection.

`WinUIDragAndDropAdapter` also replaces `latestPointerPoint` without releasing
the previous point and clears the field without releasing the retained point.

The kotlin-winrt generated wrappers hold an `_inner` reference and a separate
lazy `_defaultInterface`. `nativeObject.close()` closes `_inner`, but the
reference produced by `acquireInterfaceReference()` is not owned by `_inner`
and is therefore left live. The main COM reference also has no general
context-safe finalization path that Compose can rely on for either target.

## CsWinRT Reference Behavior

CsWinRT does not dispose event arguments at the end of every generated event
stub. `MarshalInspectable.FromAbi` creates or reuses a `ComWrappers` RCW, so a
consumer may retain an event argument after the callback.

Its ownership model still closes the references eventually:

- each RCW owns its native object reference;
- queried interfaces move into the RCW's query-interface cache;
- `IObjectReference` has idempotent `Dispose` and a finalizer;
- native references contribute GC memory pressure;
- apartment-bound releases are dispatched back to their original context.

kotlin-winrt does not yet provide that complete RCW lifecycle. This design
therefore follows CsWinRT's object-ownership shape but uses deterministic
cleanup at the Compose pointer boundary. It does not change kotlin-winrt's
global delegate-argument lifetime or make all event arguments callback-scoped.

## Scope

The implementation has two narrowly bounded parts:

1. Fix kotlin-winrt so interface references returned by
   `acquireInterfaceReference(parent, iid)` are owned by `parent` and are
   closed when the parent closes.
2. Fix compose-winui pointer and drag-and-drop code so every transient pointer
   projection is closed or explicitly transferred to one long-lived owner.

Record the projection ownership defect as `KWINRT-064` in
`kotlin-winrt-issues.md` and reference that ID from narrowly scoped Compose
cleanup comments where the projection lifetime is otherwise non-obvious.

## Non-Goals

- Do not audit or rewrite every compose-winui WinRT call site.
- Do not add a global callback lease to kotlin-winrt delegate invocation.
- Do not add JVM Cleaner or Kotlin/Native finalizer behavior without a
  context-safe COM release design.
- Do not call pointer APIs through hard-coded ABI vtable slots.
- Do not remove historical pointer data or disable `getIntermediatePoints` to
  hide the allocation rate.
- Do not change Compose pointer dispatch, capture, focus, or drag semantics.

## kotlin-winrt Ownership

`ComObjectReference` gains an internal owned-child-reference registry.
`acquireInterfaceReference(parent, iid)` registers the acquired QI reference
with that parent before returning it. Closing the parent closes every
registered child and then closes the parent's own COM pointer.

The registry must satisfy these rules:

- parent and child close operations are idempotent;
- manually closing a child before its parent is safe;
- every child registered before parent disposal is visited exactly once;
- a child acquired while parent disposal wins the race is closed immediately
  and the acquisition reports the parent as disposed;
- all children are attempted even when one close fails;
- the first close failure is preserved and later failures are suppressed.

This is intentionally narrower than a general finalization system. It closes
the private default interface when Compose explicitly closes a projection's
`nativeObject`, while preserving the existing ability to retain projected
objects outside a delegate callback.

## Pointer Event Ownership

The `PointerEventHandler` implementation treats the native event arguments as
valid only for its synchronous Compose dispatch. It closes the event
argument's native reference in a `finally` block after dispatch completes.
The `nativeEvent` value remains available during that synchronous dispatch but
is not a platform-owned object that may be retained after the callback.

Pointer conversion separates primitive Compose data from owned native
resources:

1. Obtain the current point and its properties.
2. Read position, timestamp, pointer type, pressure, buttons, keyboard
   modifiers, scroll values, and hover state into Kotlin values.
3. Read intermediate points by index, excluding WinUI's final current point.
4. Convert each fetched historical point to `HistoricalChange` and close it in
   `finally`.
5. Close the intermediate collection after all fetched elements have been
   handled.
6. Close current-point properties after their last use.
7. Either transfer the current point to the drag adapter or close it before
   leaving the handler.

The resulting pointer-state tracker and `WinUIPointerEvent` retain Kotlin
primitives and Compose values only. They do not retain transient collection
elements or pointer properties.

Historical conversion preserves existing failure behavior: projection or
coordinate failure produces an empty historical list. Cleanup still runs for
the collection and every element already fetched.

## Drag Pointer Ownership

The current point must be available before Compose pointer dispatch because a
drag source can start synchronously from that dispatch. Ownership is therefore
transferred to `WinUIDragAndDropAdapter` immediately before dispatch.

`updateSourcePointerPoint` becomes an explicit ownership-replacement boundary:

- a non-null argument is owned by the drag adapter after the call succeeds;
- replacing a point swaps state first and then closes the old point;
- setting null, canceling input, completing a drag, and disposing the adapter
  all clear and close the retained point;
- passing the identical wrapper instance is a no-op;
- if transfer fails, the pointer adapter closes the point.

The adapter never retains more than one source point.

## Error Handling

Cleanup uses `try/finally` or an equivalent close helper so dispatch failures
cannot skip reference release. Batch cleanup attempts every resource. If
normal work and cleanup both fail, the normal failure remains primary and
cleanup failures are suppressed. If only cleanup fails, the first cleanup
failure is reported.

State is cleared before closing retained resources. This makes cancel and
dispose idempotent even when COM release unexpectedly fails. A newly accepted
drag point remains installed if closing the previous point fails.

Normal COM `Release` is not expected to fail. The error rules exist to prevent
one malformed or already-disposed projection from leaking all resources later
in the cleanup sequence.

## Testing

Implementation proceeds test-first.

### kotlin-winrt runtime tests

- parent close releases an acquired child interface exactly once;
- closing a child before its parent does not double-release it;
- multiple acquired interfaces all close when the parent closes;
- a child acquisition racing with parent disposal cannot escape the registry;
- close attempts continue after a child failure and preserve failure order;
- retained projected objects remain usable outside a delegate callback until
  their owner is explicitly closed.

### compose-winui tests

- current point properties close after conversion;
- intermediate collection and each fetched element close on success;
- partial historical conversion failure closes all resources already owned;
- drag point replacement closes the previous point exactly once;
- null, cancel, drag completion, and dispose close the retained point;
- a failed ownership transfer returns cleanup responsibility to the pointer
  adapter;
- pointer dispatch still receives pressure, hover, historical, capture, and
  native-event data synchronously.

Small generic ownership helpers are acceptable when they make reference counts
testable, but production pointer behavior must also be covered. Tests must not
assert only against mocks of the code under test.

### Runtime memory regression

Use the existing local pointer input pump with pointer debug logging disabled.
For JVM, force a full GC before the warm-up, after the warm-up, and after each
measured round. Run one warm-up round followed by at least three rounds of at
least 4,000 effective pointer inputs each.

The measured post-GC private-byte values must plateau: aggregate growth from
the first measured round to the final measured round must be at most 5 MB, and
must not show a repeated positive slope proportional to event count. Managed
heap after GC must return near its pre-round live-set size.

Run the equivalent warm-up and repeated pointer rounds against the MinGW MPP
sample. Without a managed full-GC command, compare private bytes after equal
idle settling periods. The measured rounds must plateau within the same 5 MB
aggregate tolerance instead of reproducing continuous event-count growth.

Generated CSV files, process dumps, logs, and screenshots stay under
`.agent_tmp/` and remain untracked.

### Required repository validation

Use JDK 25 and `--no-configuration-cache` for WinUI Gradle tasks. At minimum:

- compile `:compose:ui:ui` for `winuiJvm`;
- run focused `winuiJvmTest` coverage;
- run `compose/ui/ui/winui-samples` through its repository task;
- build and run the WinUI JVM MPP sample used for the memory reproduction;
- compile the shared change for `winuiMingw` and run the MinGW MPP sample when
  the configured kotlin-winrt MinGW runtime is available;
- confirm existing pointer, drag-and-drop, capture, and disposal smokes remain
  green.

## Success Criteria

- Runtime tests prove that every transient parent and derived interface
  reference is released exactly once.
- Pointer and drag tests prove ownership on success, failure, cancellation,
  replacement, and disposal paths.
- Historical pointer data and synchronous drag start behavior remain intact.
- The JVM memory reproduction no longer retains approximately 22.7 MB per
  3,907 pointer inputs and meets the plateau criterion.
- The MinGW sample also plateaus after warm-up.
- Required WinUI JVM compilation, tests, and repository-local sample complete
  successfully with configuration cache disabled.
- No ABI-slot workaround, process-shell fallback, global event lifetime
  change, or unrelated compose-winui ownership audit is introduced.
